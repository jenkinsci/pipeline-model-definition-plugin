package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import hudson.model.Describable;
import hudson.model.Descriptor;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;

/**
 * A representation of a method call, including its name and a list of {@link ModelASTMethodArg}s.
 *
 * This is used for things like job properties, triggers and parameter definitions, allowing parsing and validation of
 * the arguments in case they themselves are method calls.
 *
 * @author Andrew Bayer
 */
public class ModelASTMethodCall extends ModelASTElement implements ModelASTMethodArg {
    private String name;
    private List<ModelASTMethodArg> args = new ArrayList<ModelASTMethodArg>();

    public static Map<String, String> getBlockedSteps() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("node", Messages.ModelASTMethodCall_BlockedSteps_Node());
        map.putAll(ModelASTStep.getBlockedSteps());
        return map;
    }

    public ModelASTMethodCall(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTMethodArg arg: args) {
            a.add(arg.toJSON());
        }
        return new JSONObject().accumulate("name", name).accumulate("arguments", a);
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTMethodArg arg : args) {
            arg.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder(name);
        result.append('(');
        boolean first = true;
        for (ModelASTMethodArg arg : args) {
            if (first) {
                first = false;
            } else {
                result.append(", ");
            }
            result.append(arg.toGroovy());
        }
        result.append(')');
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTMethodArg arg : args) {
            arg.removeSourceLocation();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ModelASTMethodArg> getArgs() {
        return args;
    }

    public void setArgs(List<ModelASTMethodArg> args) {
        this.args = args;
    }

    @Override
    public Object toRuntime() {
        Map<String,Object> m = new LinkedHashMap<>();
        List<Object> l = new ArrayList<>();
        for (ModelASTMethodArg a : args) {
            l.add(a.toRuntime());
        }
        m.put(name, l);
        return m;
    }

    @Override
    public String toString() {
        return "ModelASTMethodCall{" +
                "name='" + name + '\'' +
                ", args=" + args +
                "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ModelASTMethodCall that = (ModelASTMethodCall) o;

        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
            return false;
        }
        return getArgs() != null ? getArgs().equals(that.getArgs()) : that.getArgs() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getArgs() != null ? getArgs().hashCode() : 0);
        return result;
    }

    public Describable instantiate(Class<? extends Describable> base) throws Exception {
        Map<String, Object> inMap = getArgsAsMap(base, true);
        if (inMap != null) {
            DescriptorLookupCache lookup = DescriptorLookupCache.getPublicCache();
            Descriptor desc = lookup.lookupFunction(base, name);
            DescribableModel<? extends Describable> model = null;
            if (desc != null) {
                model = lookup.modelForDescribable(base, name);
            }
            if (model != null) {
                return model.instantiate(inMap);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> getArgsAsMap(Class<? extends Describable> base, boolean instantiate) throws Exception {
        DescriptorLookupCache lookup = DescriptorLookupCache.getPublicCache();
        Descriptor desc = lookup.lookupFunctionFirstThenStep(base, name);
        DescribableModel<? extends Describable> model = null;
        if (desc != null) {
            model = lookup.modelForFunctionFirstThenStep(base, name);
        }

        Map<String,Object> inMap = new LinkedHashMap<>();

        if (model != null) {
            boolean hasPair = false;
            for (ModelASTMethodArg testArg : args) {
                if (testArg instanceof ModelASTKeyValueOrMethodCallPair) {
                    hasPair = true;
                }
            }
            if (hasPair) {
                for (ModelASTMethodArg a : args) {
                    if (!(a instanceof ModelASTKeyValueOrMethodCallPair)) {
                        // TODO: Decide whether to throw an exception instead of just returning?
                        return null;
                    }

                    ModelASTKeyValueOrMethodCallPair kvm = (ModelASTKeyValueOrMethodCallPair) a;

                    DescribableParameter p = model.getParameter(kvm.getKey().getKey());
                    if (p == null) {
                        // TODO: Decide whether to throw an exception instead of just returning?
                        return null;
                    }
                    Class<? extends Describable> childBase = Describable.class;
                    if (Describable.class.isAssignableFrom(p.getErasedType())) {
                        childBase = p.getErasedType();
                    }

                    // TODO: Decide whether we error out if there's a ModelASTKeyValueOrMethodCallPair or ModelASTClosureMap for the value
                    if (kvm.getValue() instanceof ModelASTMethodCall) {
                        if (instantiate) {
                            inMap.put(kvm.getKey().getKey(),
                                    ((ModelASTMethodCall) kvm.getValue()).instantiate(childBase));
                        } else {
                            inMap.put(kvm.getKey().getKey(), ((ModelASTMethodCall) kvm.getValue()).getArgsAsMap(childBase, false));
                        }
                    } else if (kvm.getValue() instanceof ModelASTValue) {
                        inMap.put(kvm.getKey().getKey(), ((ModelASTValue) kvm.getValue()).getValue());
                    }
                }
            } else {
                List<DescribableParameter> requiredParams = new ArrayList<>();

                for (DescribableParameter p : model.getParameters()) {
                    if (p.isRequired()) {
                        requiredParams.add(p);
                    }
                }
                if (args.size() >= requiredParams.size()) {
                    for (int i = 0; i < requiredParams.size(); i++) {
                        ModelASTMethodArg argVal = args.get(i);
                        DescribableParameter p = requiredParams.get(i);
                        Class<? extends Describable> childBase = Describable.class;
                        if (Describable.class.isAssignableFrom(p.getErasedType())) {
                            childBase = p.getErasedType();
                        }

                        // TODO: Decide whether we error out if there's a ModelASTKeyValueOrMethodCallPair or ModelASTClosureMap for the value
                        if (argVal instanceof ModelASTMethodCall) {
                            if (instantiate) {
                                inMap.put(p.getName(), ((ModelASTMethodCall) argVal).instantiate(childBase));
                            } else {
                                inMap.put(p.getName(), ((ModelASTMethodCall) argVal).getArgsAsMap(childBase, false));
                            }
                        } else if (argVal instanceof ModelASTValue) {
                            inMap.put(p.getName(), ((ModelASTValue) argVal).getValue());
                        }
                    }
                }
            }
        }
        return inMap;
    }

    public Object argsAsObject(Class<? extends Describable> base, boolean instantiate) throws Exception {
        if (args == null || args.isEmpty()) {
            return null;
        } else if (args.size() == 1) {
            return args.get(0).toRuntime();
        } else {
            return getArgsAsMap(base, instantiate);
        }
    }


}
