package org.jenkinsci.plugins.pipeline.modeldefinition;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class DeclarativePipeline2Test {
    @Rule
    public RealJenkinsRule r = new RealJenkinsRule();

    @Test
    public void smokes() throws Throwable {
        r.then(DeclarativePipeline2Test::smokes_run);
    }

    private static void smokes_run(JenkinsRule j) throws Throwable {
        final WorkflowRun run = runPipeline(j, m(
                "pipeline {",
                "  agent none",
                "  options {",
                "    disableRestartFromStage()",
                "  }",
                "  stages {",
                "    stage('Example') {",
                "      steps {",
                "        echo 'Ran on RealJenkins right here!'",
                "      }",
                "    }",
                "  }",
                "}"));

        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Ran on RealJenkins right here!", run);
    }

    /**
     * Run a pipeline job synchronously.
     *
     * @param definition the pipeline job definition
     * @return the started job
     */
    private static WorkflowRun runPipeline(JenkinsRule j, String definition) throws Exception {
        final WorkflowJob project = j.createProject(WorkflowJob.class, "example");
        project.setDefinition(new CpsFlowDefinition(definition, true));
        final WorkflowRun workflowRun = project.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(workflowRun);
        return workflowRun;
    }

    /**
     * Approximates a multiline string in Java.
     *
     * @param lines the lines to concatenate with a newline separator
     * @return the concatenated multiline string
     */
    private static String m(String... lines) {
        return String.join("\n", lines);
    }
}
