package io.vertx.grpc.plugin.generation.generators;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;
import io.vertx.grpc.plugin.generation.*;
import io.vertx.grpc.plugin.schema.SchemaConfigLoader;
import io.vertx.grpc.plugin.schema.SchemaModelBuilder;

import java.io.IOException;
import java.util.*;

/**
 * Generator for OpenAPI specifications.
 * <p>
 * This generator uses the Swagger Core library to build an OpenAPI model programmatically from gRPC service definitions and serializes it to the requested formats (JSON, YAML, or
 * both).
 * <p>
 * Supports two modes:
 * <ul>
 *   <li>Merge mode (default): All services combined into a single openapi.json/yaml file</li>
 *   <li>Split mode: Each service gets its own ServiceName-openapi.json/yaml file</li>
 * </ul>
 * Files are always generated in the protobuf root folder.
 */
public class SchemaGenerator extends CodeGenerator {

  public SchemaGenerator() {
    super(GenerationType.OPENAPI);
  }

  @Override
  public GenerationResult generate(GenerationContext context) {
    List<GeneratedFile> files = new ArrayList<>();
    Set<SchemaOutputFormat> formats = context.getOptions().getSchemaOutputFormats();

    if (formats.isEmpty()) {
      return GenerationResult.success(files);
    }

    try {
      // Load config file if specified
      OpenAPI config = loadConfig(context.getOptions().getSchemaConfigFile());

      if (context.getOptions().isSchemaAllowMerge()) {
        generateMergedSpec(context, formats, files, config);
      } else {
        generateSplitSpecs(context, formats, files, config);
      }
    } catch (Exception e) {
      return GenerationResult.failure(List.of(
        new GenerationError("Failed to generate OpenAPI spec: " + e.getMessage(), e, generationType)
      ));
    }

    return GenerationResult.success(files);
  }

  /**
   * Loads the OpenAPI configuration from a file if specified.
   *
   * @param configFile the path to the config file, or null
   * @return the loaded OpenAPI config, or null if no config file specified
   * @throws IOException if there's an error loading the config file
   */
  private OpenAPI loadConfig(String configFile) throws IOException {
    if (configFile == null || configFile.isEmpty()) {
      return null;
    }
    return SchemaConfigLoader.load(configFile);
  }

  private void generateMergedSpec(GenerationContext context, Set<SchemaOutputFormat> formats,
    List<GeneratedFile> files, OpenAPI config) {
    OpenAPI openAPI = SchemaModelBuilder.build(
      context.getServices(),
      context.getPackageName(),
      context.getMessageDescriptors(),
      config
    );

    if (openAPI == null) {
      return;
    }

    for (SchemaOutputFormat format : formats) {
      String content = serializeOpenApi(openAPI, format);
      String fileName = "openapi." + format.getExtension();
      // Empty relative path = protobuf root folder
      GeneratedFile file = new GeneratedFile(fileName, "", content, generationType);
      files.add(file);
    }
  }

  private void generateSplitSpecs(GenerationContext context, Set<SchemaOutputFormat> formats,
    List<GeneratedFile> files, OpenAPI config) {
    for (ServiceDescriptor service : context.getServices()) {
      if (service.getTranscodingMethods().isEmpty()) {
        continue;
      }

      OpenAPI openAPI = SchemaModelBuilder.build(
        Collections.singletonList(service),
        context.getPackageName(),
        context.getMessageDescriptors(),
        config
      );

      if (openAPI == null) {
        continue;
      }

      String serviceName = service.getName();
      for (SchemaOutputFormat format : formats) {
        String content = serializeOpenApi(openAPI, format);
        String fileName = serviceName + "-openapi." + format.getExtension();
        // Empty relative path = protobuf root folder
        GeneratedFile file = new GeneratedFile(fileName, "", content, generationType);
        files.add(file);
      }
    }
  }

  private String serializeOpenApi(OpenAPI openAPI, SchemaOutputFormat format) {
    if (Objects.requireNonNull(format) == SchemaOutputFormat.OPEN_API_YAML) {
      return Yaml.pretty(openAPI);
    }
    return Json.pretty(openAPI);
  }
}
