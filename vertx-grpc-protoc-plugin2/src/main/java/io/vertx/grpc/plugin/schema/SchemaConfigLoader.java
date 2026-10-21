package io.vertx.grpc.plugin.schema;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads OpenAPI configuration from YAML or JSON files.
 * <p>
 * The configuration file should contain OpenAPI specification fields that will be merged
 * with the generated specification. Supported fields include:
 * <ul>
 *   <li>info - API metadata (title, description, version, contact, license, termsOfService)</li>
 *   <li>servers - Server URLs and descriptions</li>
 *   <li>security - Global security requirements</li>
 *   <li>tags - Tag definitions with descriptions</li>
 *   <li>externalDocs - External documentation links</li>
 *   <li>components.securitySchemes - Security scheme definitions</li>
 * </ul>
 * <p>
 * Example YAML configuration:
 * <pre>
 * openapi: 3.0.0
 * info:
 *   title: My API
 *   version: 2.0.0
 *   description: My custom API description
 *   contact:
 *     name: Support
 *     email: support@example.com
 * servers:
 *   - url: https://api.example.com
 *     description: Production server
 * security:
 *   - bearerAuth: []
 * components:
 *   securitySchemes:
 *     bearerAuth:
 *       type: http
 *       scheme: bearer
 * </pre>
 */
public class SchemaConfigLoader {

  /**
   * Loads an OpenAPI configuration from a file.
   * The file format is determined by the file extension (.yaml, .yml, or .json).
   *
   * @param filePath the path to the configuration file
   * @return the parsed OpenAPI configuration, or null if the file doesn't exist or is empty
   * @throws IOException if there's an error reading the file
   * @throws IllegalArgumentException if the file format is not supported
   */
  public static OpenAPI load(String filePath) throws IOException {
    if (filePath == null || filePath.isEmpty()) {
      return null;
    }

    Path path = Paths.get(filePath);
    if (!Files.exists(path)) {
      throw new IOException("Schema config file not found: " + filePath);
    }

    String content = Files.readString(path);
    if (content == null || content.trim().isEmpty()) {
      return null;
    }

    String lowerPath = filePath.toLowerCase();
    if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
      return parseYaml(content);
    } else if (lowerPath.endsWith(".json")) {
      return parseJson(content);
    } else {
      // Try to detect format from content
      String trimmed = content.trim();
      if (trimmed.startsWith("{")) {
        return parseJson(content);
      } else {
        return parseYaml(content);
      }
    }
  }

  /**
   * Parses OpenAPI configuration from YAML content.
   *
   * @param yamlContent the YAML content to parse
   * @return the parsed OpenAPI configuration
   * @throws IOException if there's an error parsing the content
   */
  public static OpenAPI parseYaml(String yamlContent) throws IOException {
    try {
      return Yaml.mapper().readValue(yamlContent, OpenAPI.class);
    } catch (Exception e) {
      throw new IOException("Failed to parse YAML config: " + e.getMessage(), e);
    }
  }

  /**
   * Parses OpenAPI configuration from JSON content.
   *
   * @param jsonContent the JSON content to parse
   * @return the parsed OpenAPI configuration
   * @throws IOException if there's an error parsing the content
   */
  public static OpenAPI parseJson(String jsonContent) throws IOException {
    try {
      return Json.mapper().readValue(jsonContent, OpenAPI.class);
    } catch (Exception e) {
      throw new IOException("Failed to parse JSON config: " + e.getMessage(), e);
    }
  }
}
