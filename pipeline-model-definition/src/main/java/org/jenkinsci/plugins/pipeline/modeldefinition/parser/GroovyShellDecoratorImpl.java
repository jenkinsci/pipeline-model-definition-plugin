package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import com.cloudbees.groovy.cps.NonCPS;
import hudson.Extension;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;
import org.jenkinsci.plugins.workflow.libs.Library;
import org.jenkinsci.plugins.workflow.libs.LibraryDecorator;

import javax.annotation.CheckForNull;

/**
 * Registers the 'pipeline' step validation during Jenkinsfile parsing.
 *
 * @author Kohsuke Kawaguchi
 */
// Ordinal is set so this kicks in before other GroovyShellDecorators, such as the @Library handling
@Extension(ordinal = 1000)
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {
    @Override
    public void configureCompiler(@CheckForNull CpsFlowExecution context, CompilerConfiguration cc) {
        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports(NonCPS.class.getPackage().getName());
        ic.addStarImports("hudson.model","jenkins.model");
        ic.addImports(Library.class.getName());

        this.customizeImports(context, ic);
        cc.addCompilationCustomizers(ic);

        cc.addCompilationCustomizers(addLibsAndImports(true));

        cc.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.CANONICALIZATION) {
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

    /**
     * Runs before the {@link LibraryDecorator}, adding library annotations and imports to the AST as needed.
     *
     * @return the {@link CompilationCustomizer}
     */
    public static CompilationCustomizer addLibsAndImports(final boolean fromCps) {
        return new CompilationCustomizer(CompilePhase.CONVERSION) {
            @Override
            public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
                if (classNode.getNameWithoutPackage().equals(Converter.PIPELINE_SCRIPT_NAME)) {
                    new ModelParser(source).addImportsToAST(fromCps);
                }
            }
        };
    }
}
