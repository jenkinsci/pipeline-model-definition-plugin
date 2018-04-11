package org.jenkinsci.plugins.pipeline.modeldefinition.validator

import net.sf.json.JSONArray
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMarkerInterface

/**
 * Abstract class for collecting parse-time errors.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class ErrorCollector {
    abstract void error(ModelASTElement src, String message)

    abstract void error(ModelASTMarkerInterface src, String message)

    abstract int getErrorCount()

    abstract List<String> errorsAsStrings()

    abstract JSONArray asJson()
}
