package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Closure;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * A container for one or more {@link ModelASTJobProperty}s
 *
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public final class ModelASTJobProperties extends ModelASTElement {
    public ModelASTJobProperties(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        DefaultGroovyMethods.each(properties, new Closure<Boolean>(this, this) {
            public Boolean doCall(Object s) {
                return a.add(((ModelASTJobProperty) s).toJSON());
            }

        });

        return new JSONObject().accumulate("properties", a);
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
        DefaultGroovyMethods.each(properties, new Closure<Object>(this, this) {
            public void doCall(Object s) {
                ((ModelASTJobProperty) s).validate(validator);
            }

        });
    }

    @Override
    public String toGroovy() {
        return "jobProperties {\n" + DefaultGroovyMethods
                .join(DefaultGroovyMethods.collect(properties, new Closure<String>(this, this) {
                    public String doCall(ModelASTJobProperty it) {
                        return it.toGroovy();
                    }

                    public String doCall() {
                        return doCall(null);
                    }

                }), "\n") + "\n}\n";
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        DefaultGroovyMethods.each(properties, new Closure<Object>(this, this) {
            public void doCall(ModelASTJobProperty it) {
                it.removeSourceLocation();
            }

            public void doCall() {
                doCall(null);
            }

        });
    }

    public ArrayList<ModelASTJobProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<ModelASTJobProperty> properties) {
        this.properties = properties;
    }

    private List<ModelASTJobProperty> properties = new ArrayList<ModelASTJobProperty>();
}
