package org.jenkinsci.plugins.pipeline.modeldefinition;

import groovy.lang.Binding;
import hudson.model.Run;
import org.codehaus.groovy.control.CompilationFailedException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

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
    private final CpsScript workflowScript;

    @Whitelisted
    public RuntimeContainerBase(@Nonnull CpsScript workflowScript) throws IOException {
        super();
        this.workflowScript = workflowScript;
        this.setBinding(workflowScript.getBinding());
    }

    @Override
    public Object run() {
        throw new AssertionError("This is a helper script class.  It cannot be run.");
    }

    @Override
    public Binding getBinding() {
        // When this class is first instantiated, getBinding is called inside super()
        // At that time we get whatever binding has been set.
        // Once workflowScript is set, we always use that binding.
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
