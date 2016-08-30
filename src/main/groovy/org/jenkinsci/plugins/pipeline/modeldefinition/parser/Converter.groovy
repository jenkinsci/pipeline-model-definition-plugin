/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.pipeline.modeldefinition.parser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule
import com.github.fge.jsonschema.exceptions.ProcessingException
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.fge.jsonschema.report.ProcessingReport
import net.sf.json.JSONObject
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef

import java.security.CodeSource
import java.security.cert.Certificate

import static groovy.lang.GroovyShell.DEFAULT_CODE_BASE
import static org.codehaus.groovy.control.Phases.CANONICALIZATION

/**
 * Utilities for converting from/to {@link ModelASTPipelineDef} and raw Pipeline script.
 *
 * @author Andrew Bayer
 */
public class Converter {

    public static final String PIPELINE_SCRIPT_NAME = "WorkflowScript"

    /**
     * Validate provided {@link net.sf.json.JSONObject} against the JSON schema.
     *
     * @param origJson A {@link net.sf.json.JSONObject}, which will be converted to a Jackson {@link JsonNode} along the way.
     * @return A {@link ProcessingReport} with the results of the validation.
     * @throws ProcessingException If an error of high enough severity is detected in processing.
     */
    public static ProcessingReport validateJSONAgainstSchema(JSONObject origJson) throws ProcessingException {
        JsonNode jsonNode = jacksonJSONFromJSONObject(origJson);

        JsonSchema schema = getJSONSchema();

        return schema.validate(jsonNode)
    }

    /**
     * Converts a net.sf.json {@link JSONObject} into a Jackson {@link JsonNode} for use in schema validation.
     *
     * @param input A {@link JSONObject}
     * @return The converted {@link JsonNode}
     */
    public static JsonNode jacksonJSONFromJSONObject(JSONObject input) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JsonOrgModule());

        return mapper.valueToTree(input);
    }


    /**
     * Get the Pipeline Config AST JSON schema.
     *
     * @return the schema in {@link JsonSchema} form.
     * @throws ProcessingException
     */
    public static JsonSchema getJSONSchema() throws ProcessingException {
        final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        return factory.getJsonSchema("resource:/ast-schema.json");
    }

    /**
     * Converts a script at a given URL into {@link ModelASTPipelineDef}
     *
     * @param src A URL pointing to a Pipeline script
     * @return the converted script
     */
    public static ModelASTPipelineDef urlToPipelineDef(URL src) {
        CompilationUnit cu = new CompilationUnit(
            CompilerConfiguration.DEFAULT,
            new CodeSource(src, new Certificate[0]),
            new GroovyClassLoader());
        cu.addSource(src);

        return compilationUnitToPipelineDef(cu)
    }

    /**
     * Converts a string containing a Pipeline script into {@link ModelASTPipelineDef}
     *
     * @param script A string containing a Pipeline script
     * @return the converted script
     */
    public static ModelASTPipelineDef scriptToPipelineDef(String script) {
        CompilationUnit cu = new CompilationUnit(
            CompilerConfiguration.DEFAULT,
            new CodeSource(new URL("file", "", DEFAULT_CODE_BASE), (Certificate[]) null),
            new GroovyClassLoader())
        cu.addSource(PIPELINE_SCRIPT_NAME, script)

        return compilationUnitToPipelineDef(cu)
    }

    /**
     * Takes a {@link CompilationUnit}, copmiles it with the {@link ModelParser} injected, and returns the resulting
     * {@link ModelASTPipelineDef}
     *
     * @param cu {@link CompilationUnit} assembled by another method.
     * @return The converted script
     */
    private static ModelASTPipelineDef compilationUnitToPipelineDef(CompilationUnit cu) {
        final ModelASTPipelineDef[] model = new ModelASTPipelineDef[1];

        cu.addPhaseOperation(new CompilationUnit.SourceUnitOperation() {
            @Override
            public void call(SourceUnit source) throws CompilationFailedException {
                model[0] = new ModelParser(source).parse();
            }
        }, CANONICALIZATION);

        cu.compile(CANONICALIZATION);

        return model[0];
    }
}
