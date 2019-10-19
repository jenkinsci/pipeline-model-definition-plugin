package org.jenkinsci.plugins.pipeline.modeldefinition;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Binding;
import groovy.lang.GroovyObject;
import hudson.model.Run;
import org.codehaus.groovy.control.CompilationFailedException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * IMPORTANT: This class must inherit from CpsScript or Declarative Pipeline will halt and catch fire.
 * Examples:
 * <ul>
 * <li> this class must inherit the sleep method from CpsScript (or implement the same behavior itself).
 * <li> script step resolution will be unable to find globals
 * <li> Groovy's getProperty is not whitelisted by default except for on CpsScript
 * </ul>
 *
 * @author Liam Newman
 */
public class RuntimeContainerBase extends CpsScript {
    private CpsScript workflowScript;

    @Whitelisted
    public RuntimeContainerBase(CpsScript workflowScript) throws IOException {
        super();
        this.workflowScript = workflowScript;
        this.setBinding(workflowScript.getBinding());
    }

    @Override
    public Object run() {
        return null;
    }

    @Override
    public Binding getBinding() {
        // TODO: Comment on why this is here
        if (workflowScript != null) {
            return workflowScript.getBinding();
        }
        return super.getBinding();
    }

    @Override
    public void setBinding(Binding binding) {
        super.setBinding(binding);
    }

    @Override
    public Object getProperty(String propertyName) {
        return workflowScript.getProperty(propertyName);
    }

    @Override
    public void setProperty(String propertyName, Object newValue) {
        workflowScript.setProperty(propertyName, newValue);
    }

    @Override
    public Run<?, ?> $build() throws IOException {
        return workflowScript.$build();
    }

    @Override
    public Run<?, ?> $buildNoException() {
        return workflowScript.$buildNoException();
    }

    @Override
    public Object evaluate(String script) throws CompilationFailedException {
        return workflowScript.evaluate(script);
    }

    @Override
    public Object evaluate(File file) throws CompilationFailedException, IOException {
        return workflowScript.evaluate(file);
    }

    @Override
    public void run(File file, String[] arguments) throws CompilationFailedException, IOException {
        workflowScript.run(file, arguments);
    }
}
