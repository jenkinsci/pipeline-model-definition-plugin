package org.jenkinsci.plugins.pipeline.config.validator

import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTElement;

/**
 * Abstract class for collecting parse-time errors.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ErrorCollector {
    public abstract void error(ConfigASTElement src, String message);

    public abstract int getErrorCount()

    public abstract List<String> errorsAsStrings()
}
