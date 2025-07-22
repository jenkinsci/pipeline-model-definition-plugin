/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.Functions;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.util.VersionNumber;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import static org.junit.Assume.assumeFalse;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.TailLog;

public final class UpgradeTest {

    @Rule public RealJenkinsRule rr = new RealJenkinsRule().withLogger(Upgrade.class, Level.FINER);
    @Rule public InboundAgentRule iar = new InboundAgentRule();

    @Test public void deserLabelScript() throws Throwable {
        assumeFalse("TODO sh would need to be ported to bat", Functions.isWindows());
        var plugins = rr.getHome().toPath().resolve("plugins");
        Files.move(plugins.resolve("pipeline-stage-tags-metadata.jpi"), plugins.resolve("pipeline-stage-tags-metadata.jpi.orig"));
        Files.move(plugins.resolve("pipeline-model-api.jpi"), plugins.resolve("pipeline-model-api.jpi.orig"));
        Files.move(plugins.resolve("pipeline-model-extensions.jpi"), plugins.resolve("pipeline-model-extensions.jpi.orig"));
        Files.move(plugins.resolve("pipeline-model-definition.jpl"), plugins.resolve("pipeline-model-definition.jpl.orig"));
        for (var plugin : List.of("pipeline-stage-tags-metadata", "pipeline-model-api", "pipeline-model-extensions", "pipeline-model-definition")) {
            try (var is = UpgradeTest.class.getResourceAsStream("/old-releases/" + plugin + ".hpi")) {
                Files.copy(is, plugins.resolve(plugin + ".jpi"));
            }
        }
        rr.startJenkins();
        var oldVersion = rr.call(r -> {
            var version = r.jenkins.pluginManager.getPlugin("pipeline-model-definition").getVersionNumber();
            assertThat(r.jenkins.pluginManager.getPlugin("pipeline-model-extensions").getVersionNumber(), is(version));
            return version.toString();
        });
        iar.createAgent(rr, InboundAgentRule.Options.newBuilder().name("remote").color(PrefixedOutputStream.Color.YELLOW).build());
        try (var tail = new TailLog(rr, "p", 1).withColor(PrefixedOutputStream.Color.MAGENTA)) {
            rr.run(r -> {
                var p = r.createProject(WorkflowJob.class, "p");
                p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("PROCEED")));
                p.setDefinition(new CpsFlowDefinition(
                    """
                    pipeline {
                      agent {
                        label 'remote'
                      }
                      stages {
                        stage('all') {
                          steps {
                            sh '''
                              set +x
                              echo waiting for a signal at "$PROCEED"
                              until [ -f "$PROCEED" ]
                              do
                                date
                                sleep 5
                              done
                            '''
                          }
                        }
                      }
                    }
                    """, true));
                var b = p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("PROCEED", new File(r.jenkins.root, "proceed").getAbsolutePath()))).waitForStart();
                r.waitForMessage("waiting for a signal", b);
            });
            rr.stopJenkins();
            Files.move(plugins.resolve("pipeline-stage-tags-metadata.jpi.orig"), plugins.resolve("pipeline-stage-tags-metadata.jpi"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(plugins.resolve("pipeline-model-api.jpi.orig"), plugins.resolve("pipeline-model-api.jpi"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(plugins.resolve("pipeline-model-extensions.jpi.orig"), plugins.resolve("pipeline-model-extensions.jpi"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(plugins.resolve("pipeline-model-definition.jpl.orig"), plugins.resolve("pipeline-model-definition.jpl"));
            Files.delete(plugins.resolve("pipeline-model-definition.jpi"));
            rr.then(r -> {
                var newVersion = r.jenkins.pluginManager.getPlugin("pipeline-model-definition").getVersionNumber();
                assertThat(newVersion, greaterThan(new VersionNumber(oldVersion)));
                assertThat(r.jenkins.pluginManager.getPlugin("pipeline-model-extensions").getVersionNumber(), is(newVersion));
                System.err.println("Upgraded from " + oldVersion + " to " + newVersion);
                var p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                var b = p.getBuildByNumber(1);
                r.waitForMessage("Resuming build at ", b);
                r.waitForMessage("Ready to run at ", b);
            });
            rr.then(r -> {
                var p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                var b = p.getBuildByNumber(1);
                var proceed = Path.of(((StringParameterValue) b.getAction(ParametersAction.class).getParameter("PROCEED")).getValue());
                System.err.println("Touching " + proceed);
                Files.writeString(proceed, "go");
                r.assertBuildStatusSuccess(r.waitForCompletion(b));
            });
            tail.waitForCompletion();
        }
    }

}
