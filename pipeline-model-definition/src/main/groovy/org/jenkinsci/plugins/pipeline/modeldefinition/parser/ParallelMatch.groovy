package org.jenkinsci.plugins.pipeline.modeldefinition.parser

import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.Statement

/**
 * Pattern match for the following Groovy construct:
 *
 * <pre><xmp>
 * parallel(
 * aaa: { ... },
 * bbb: { ... },
 * ...
 * )
 * </xmp></pre>
 *
 * @author Kohsuke Kawaguchi
 * @see ModelParser#matchParallel(Statement)
 */
class ParallelMatch {
    /**
     * ASTNode that matches the whole thing, which is a method invocation
     */
    final MethodCallExpression whole

    /**
     * Order preserving map of all the branches of parallel.
     */
    final Map<String,ClosureExpression> args = new LinkedHashMap<String, ClosureExpression>()

    /**
     * Whether this parallel invocation should fail fast
     */
    final Boolean failFast

    ParallelMatch(MethodCallExpression whole, Map<String,ClosureExpression> args, Boolean failFast) {
        this.whole = whole
        this.args.putAll(args)
        this.failFast = failFast
    }
}
