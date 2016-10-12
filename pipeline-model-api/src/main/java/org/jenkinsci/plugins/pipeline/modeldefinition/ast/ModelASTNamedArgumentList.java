package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents the named parameters for a step in a map of {@link ModelASTKey}s and {@link ModelASTValue}s.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public final class ModelASTNamedArgumentList extends ModelASTArgumentList {
    public ModelASTNamedArgumentList(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        final JSONArray a = new JSONArray();

        DefaultGroovyMethods.each(arguments, new Closure<Boolean>(this, this) {
            public Boolean doCall(Object k, Object v) {
                JSONObject o = new JSONObject();
                o.accumulate("key", ((ModelASTKey) k).toJSON());
                o.accumulate("value", ((ModelASTValue) v).toJSON());
                return a.add(o);
            }

        });
        return a;
    }

    /**
     * Checks if a given key name is present.
     *
     * @param keyName The name of a key to check for.
     * @return True if a {@link ModelASTKey} with that name is present in the map.
     */
    public boolean containsKeyName(@Nonnull final String keyName) {
        return DefaultGroovyMethods.any(arguments, new Closure<Boolean>(this, this) {
            public Boolean doCall(Object k, Object v) {
                return keyName.equals(((ModelASTKey) k).getKey());
            }

        });
    }

    @Override
    public void validate(final ModelValidator validator) {
        // Nothing to validate directly
        DefaultGroovyMethods.each(arguments, new Closure<Object>(this, this) {
            public void doCall(Object k, Object v) {
                ((ModelASTKey) k).validate(validator);
                ((ModelASTValue) v).validate(validator);
            }

        });

    }

    @Override
    public String toGroovy() {
        return DefaultGroovyMethods.join(DefaultGroovyMethods.collect(arguments, new Closure<GString>(this, this) {
            public GString doCall(final Object k, final Object v) {
                return ((ModelASTKey) k).toGroovy() + ": " + ((ModelASTValue) v).toGroovy();
            }

        }), ", ");
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();

        DefaultGroovyMethods.each(arguments, new Closure<Object>(this, this) {
            public void doCall(Object k, Object v) {
                ((ModelASTKey) k).removeSourceLocation();
                ((ModelASTValue) v).removeSourceLocation();
            }

        });
    }

    public LinkedHashMap<ModelASTKey, ModelASTValue> getArguments() {
        return arguments;
    }

    public void setArguments(Map<ModelASTKey, ModelASTValue> arguments) {
        this.arguments = arguments;
    }

    private Map<ModelASTKey, ModelASTValue> arguments = new LinkedHashMap<ModelASTKey, ModelASTValue>();
}
