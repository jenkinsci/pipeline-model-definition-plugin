package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import com.cloudbees.groovy.cps.NonCPS;
import hudson.Extension;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ModelParser;
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
    public void configureCompiler(@CheckForNull CpsFlowExecution context, CompilerConfiguration cc) {
        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports(NonCPS.class.getPackage().getName());
        ic.addStarImports("hudson.model","jenkins.model");
        this.customizeImports(context, ic);
        cc.addCompilationCustomizers(ic);

        cc.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.SEMANTIC_ANALYSIS) {
            @Override
            public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
                // TODO: workflow-cps-plugin CpsFlowExecution.parseScript() should be passing in CodeSource
                // to help us determine that that is a user-written script
                // Commenting out for findbugs
                // CodeSource cs = classNode.getCompileUnit().getCodeSource();
                // if (<< cs comes from Jenkinsfile>>) {

                // but until that happens,
                // Using getNameWithoutPackage to make sure we don't end up executing without parsing when an inadvertent package name is put in the Jenkinsfile.
                if (classNode.getNameWithoutPackage().equals(Converter.PIPELINE_SCRIPT_NAME)) {
                    new ModelParser(source).parse();
                }
            }
        });
    }
}
