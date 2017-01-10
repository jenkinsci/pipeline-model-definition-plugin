package org.jenkinsci.plugins.pipeline.modeldefinition.validator

import net.sf.json.JSONArray
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement;

/**
 * Abstract class for collecting parse-time errors.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ErrorCollector {
    public abstract void error(ModelASTElement src, String message);

    public abstract int getErrorCount()

    public abstract List<String> errorsAsStrings()

    public abstract JSONArray asJson()
}
