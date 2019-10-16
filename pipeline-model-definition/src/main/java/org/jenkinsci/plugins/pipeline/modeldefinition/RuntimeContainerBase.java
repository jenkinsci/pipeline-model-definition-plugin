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
    private static CpsScript workflowScript;
    private static final Map<Class, RuntimeContainerBase> classMap = new HashMap<Class, RuntimeContainerBase>();

    @Whitelisted
    public RuntimeContainerBase() throws IOException {
        super();
    }

    @Whitelisted
    public static void initialize(CpsScript script) {
        workflowScript = script;
    }

    @Whitelisted
    public static RuntimeContainerBase getInstance(Class instanceClass) {
        if (!classMap.containsKey(instanceClass)) {
            try {
                classMap.put(instanceClass, (RuntimeContainerBase) instanceClass.getConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return classMap.get(instanceClass);
    }

    @Override
    public Object run() {
        return null;
    }

    @Override
    public Binding getBinding() {
        return workflowScript.getBinding();
    }

    @Override
    public void setBinding(Binding binding) {
        workflowScript.setBinding(binding);
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
