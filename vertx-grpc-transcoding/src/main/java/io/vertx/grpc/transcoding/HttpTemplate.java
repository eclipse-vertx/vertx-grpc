package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.DataObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an HTTP template used in gRPC transcoding.
 * <p>
 * This interface defines methods for accessing the components of a parsed HTTP template string, including the segments, verb, and variables.
 *
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/http_template.cc">grpc-httpjson-transcoding</a>
 */
@DataObject
public class HttpTemplate {

    private final List<String> segments;
    private final String verb;
    private final List<HttpTemplateVariable> variables;

    public HttpTemplate(List<String> segments, String verb, List<HttpTemplateVariable> variables) {
        this.segments = segments;
        this.verb = verb;
        this.variables = variables;
    }

    /**
     * Parses the given HTTP template string.
     *
     * @param template The HTTP template string to parse.
     * @return The parsed {@code HttpTemplate}, or {@code null} if the parsing failed.
     */
    public static HttpTemplate parse(String template) {
        if (template.equals("/")) {
            return new HttpTemplate(new ArrayList<>(), "", new ArrayList<>());
        }

        HttpTemplateParser parser = new HttpTemplateParser(template);
        if (!parser.parse() || !parser.validateParts()) {
            return null;
        }

        return new HttpTemplate(parser.segments(), parser.verb(), parser.variables());
    }

    /**
     * Returns the list of segments in the parsed template.
     *
     * @return The list of segments.
     */
    public List<String> getSegments() {
        return segments;
    }

    /**
     * Returns the verb in the parsed template.
     *
     * @return The verb.
     */
    public String getVerb() {
        return verb;
    }

    /**
     * Returns the list of variables in the parsed template.
     *
     * @return The list of variables.
     */
    public List<HttpTemplateVariable> getVariables() {
        return variables;
    }
}
