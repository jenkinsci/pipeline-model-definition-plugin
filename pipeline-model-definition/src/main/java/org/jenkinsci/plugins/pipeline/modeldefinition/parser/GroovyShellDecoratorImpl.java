package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import com.cloudbees.groovy.cps.NonCPS;
import hudson.Extension;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

import javax.annotation.CheckForNull;

/**
 * Registers the 'pipeline' step validation during Jenkinsfile parsing.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {
    @Override
    public GroovyShellDecorator forTrusted() {
        return this;
    }

    @Override
    public void configureCompiler(@CheckForNull final CpsFlowExecution execution, CompilerConfiguration cc) {
        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports(NonCPS.class.getPackage().getName());
        ic.addStarImports("hudson.model","jenkins.model");
        this.customizeImports(execution, ic);
        cc.addCompilationCustomizers(ic);

        cc.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.SEMANTIC_ANALYSIS) {
            @Override
            public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
                // Only look for pipeline blocks in classes without package names - i.e., in vars and Jenkinsfiles. It's
                // theoretically possible to do src/Foo.groovy as well, but we'll deal with that later.
                // Also allow WorkflowScript cases with package names, since discarding those would break compatibility.
                if (classNode.getPackageName() == null || classNode.getNameWithoutPackage().equals(Converter.PIPELINE_SCRIPT_NAME)) {
                    new ModelParser(source, execution).parse();
                }
            }
        });
    }
}
