package io.vertx.grpc.plugin.generation;

/**
 * Represents the available output formats for OpenAPI specification generation.
 */
public enum SchemaOutputFormat {
  /**
   * OpenAPI JSON format output (.json extension)
   */
  OPEN_API_JSON("openapi-json", "json"),

  /**
   * OpenAPI YAML format output (.yaml extension)
   */
  OPEN_API_YAML("openapi-yaml", "yaml");

  private final String value;
  private final String extension;

  SchemaOutputFormat(String value, String extension) {
    this.value = value;
    this.extension = extension;
  }

  /**
   * Returns the string value of this format used for CLI parameter parsing.
   *
   * @return the format value (e.g., "openapi-json" or "openapi-yaml")
   */
  public String getValue() {
    return value;
  }

  /**
   * Returns the file extension for this format.
   *
   * @return the file extension (e.g., "json" or "yaml")
   */
  public String getExtension() {
    return extension;
  }

  /**
   * Parses a string value to an SchemaOutputFormat enum.
   *
   * @param value the string value to parse
   * @return the corresponding SchemaOutputFormat, or null if not found
   */
  public static SchemaOutputFormat fromValue(String value) {
    if (value == null) {
      return null;
    }
    for (SchemaOutputFormat format : values()) {
      if (format.value.equalsIgnoreCase(value)) {
        return format;
      }
    }
    return null;
  }
}
