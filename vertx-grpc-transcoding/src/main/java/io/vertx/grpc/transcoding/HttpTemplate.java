package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.grpc.transcoding.impl.HttpTemplateImpl;
import io.vertx.grpc.transcoding.impl.HttpTemplateParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an HTTP template used in gRPC transcoding.
 * <p>
 * This interface defines methods for accessing the components of a parsed HTTP template string, including the segments, verb, and variables.
 *
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/http_template.cc">grpc-httpjson-transcoding</a>
 */
@VertxGen
public interface HttpTemplate {

  /**
   * Parses the given HTTP template string.
   *
   * @param template The HTTP template string to parse.
   * @return The parsed {@code HttpTemplate}, or {@code null} if the parsing failed.
   */
  static HttpTemplate parse(String template) {
    if (template.equals("/")) {
      return new HttpTemplateImpl(new ArrayList<>(), "", new ArrayList<>());
    }

    HttpTemplateParser parser = new HttpTemplateParser(template);
    if (!parser.parse() || !parser.validateParts()) {
      return null;
    }

    return new HttpTemplateImpl(parser.segments(), parser.verb(), parser.variables());
  }

  /**
   * Returns the list of segments in the parsed template.
   *
   * @return The list of segments.
   */
  List<String> getSegments();

  /**
   * Returns the verb in the parsed template.
   *
   * @return The verb.
   */
  String getVerb();

  /**
   * Returns the list of variables in the parsed template.
   *
   * @return The list of variables.
   */
  List<HttpTemplateVariable> getVariables();
}
