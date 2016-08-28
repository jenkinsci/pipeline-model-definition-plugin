package org.jenkinsci.plugins.pipeline.config.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.config.validator.ConfigValidator

import javax.annotation.Nonnull

/**
 * Represents the named parameters for a step in a map of {@link ConfigASTKey}s and {@link ConfigASTValue}s.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public final class ConfigASTNamedArgumentList extends ConfigASTArgumentList {
    Map<ConfigASTKey,ConfigASTValue> arguments = [:]

    public ConfigASTNamedArgumentList(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public JSONArray toJSON() {
        JSONArray a = new JSONArray()

        arguments.each { k,v ->
            JSONObject o = new JSONObject()
            o.accumulate("key", k.toJSON())
            o.accumulate("value", v.toJSON())
            a.add(o)
        }
        return a
    }

    /**
     * Checks if a given key name is present.
     *
     * @param keyName The name of a key to check for.
     * @return True if a {@link ConfigASTKey} with that name is present in the map.
     */
    public boolean containsKeyName(@Nonnull String keyName) {
        return arguments.any { k, v ->
            keyName.equals(k.key)
        }
    }

    @Override
    public void validate(ConfigValidator validator) {
        // Nothing to validate directly
        arguments.each { k, v ->
            k?.validate(validator)
            v?.validate(validator)
        }

    }

    @Override
    public String toGroovy() {
        return arguments.collect { k, v ->
            "${k.toGroovy()}: ${v.toGroovy()}"
        }.join(", ")
    }
}
