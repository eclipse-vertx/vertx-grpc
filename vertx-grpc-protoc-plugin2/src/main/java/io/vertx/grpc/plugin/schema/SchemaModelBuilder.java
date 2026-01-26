package io.vertx.grpc.plugin.schema;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.vertx.grpc.plugin.descriptors.MessageDescriptor;
import io.vertx.grpc.plugin.descriptors.MethodDescriptor;
import io.vertx.grpc.plugin.descriptors.ServiceDescriptor;
import io.vertx.grpc.plugin.descriptors.TranscodingDescriptor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds an OpenAPI model programmatically from gRPC service descriptors.
 * <p>
 * This class uses the Swagger Core library to construct an OpenAPI 3.0.0 specification from gRPC service definitions that have HTTP transcoding annotations.
 */
public class SchemaModelBuilder {

  private static final String OPENAPI_VERSION = "3.0.0";
  private static final String API_VERSION = "1.0.0";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
  private static final String APPLICATION_JSON = "application/json";

  // Pattern to match path parameters with sub-paths like {name=projects/*/messages/*}
  private static final Pattern SLASHED_PATH_PARAM_PATTERN = Pattern.compile("\\{([^}=]+)=([^}]+)}");

  /**
   * Builds an OpenAPI model from the given service descriptors.
   *
   * @param services the list of gRPC service descriptors
   * @param packageName the package name to use in the API title
   * @param messageDescriptors the map of message descriptors for schema generation
   * @return the built OpenAPI model, or null if no services have transcoding methods
   */
  public static OpenAPI build(List<ServiceDescriptor> services, String packageName,
    Map<String, MessageDescriptor> messageDescriptors) {
    return build(services, packageName, messageDescriptors, null);
  }

  /**
   * Builds an OpenAPI model from the given service descriptors, merging with a configuration.
   *
   * @param services the list of gRPC service descriptors
   * @param packageName the package name to use in the API title
   * @param messageDescriptors the map of message descriptors for schema generation
   * @param config optional OpenAPI configuration to merge (info, servers, security, tags, etc.)
   * @return the built OpenAPI model, or null if no services have transcoding methods
   */
  public static OpenAPI build(List<ServiceDescriptor> services, String packageName, Map<String, MessageDescriptor> messageDescriptors, OpenAPI config) {
    List<ServiceDescriptor> transcodingServices = new ArrayList<>();

    for (ServiceDescriptor service : services) {
      if (!service.getTranscodingMethods().isEmpty()) {
        transcodingServices.add(service);
      }
    }

    if (transcodingServices.isEmpty()) {
      return null;
    }

    OpenAPI openAPI = new OpenAPI();

    openAPI.setOpenapi(OPENAPI_VERSION);
    openAPI.setInfo(buildInfo(packageName));
    openAPI.setServers(buildServers());
    openAPI.setPaths(buildPaths(transcodingServices));
    openAPI.setComponents(buildComponents(transcodingServices, messageDescriptors));

    // Merge with config if provided
    if (config != null) {
      mergeConfig(openAPI, config);
    }

    return openAPI;
  }

  /**
   * Merges configuration from a config OpenAPI into the generated OpenAPI. Config values override generated defaults for info, servers, security, tags, and externalDocs.
   * Components are merged (securitySchemes from config are added to generated schemas).
   */
  private static void mergeConfig(OpenAPI target, OpenAPI config) {
    // Override info if provided in config
    if (config.getInfo() != null) {
      target.setInfo(config.getInfo());
    }

    // Override servers if provided in config
    if (config.getServers() != null && !config.getServers().isEmpty()) {
      target.setServers(config.getServers());
    }

    // Set security if provided in config
    if (config.getSecurity() != null && !config.getSecurity().isEmpty()) {
      target.setSecurity(config.getSecurity());
    }

    // Set tags if provided in config
    if (config.getTags() != null && !config.getTags().isEmpty()) {
      target.setTags(config.getTags());
    }

    // Set externalDocs if provided in config
    if (config.getExternalDocs() != null) {
      target.setExternalDocs(config.getExternalDocs());
    }

    // Merge components (add securitySchemes from config, keep generated schemas)
    if (config.getComponents() != null) {
      Components targetComponents = target.getComponents();
      if (targetComponents == null) {
        targetComponents = new Components();
        target.setComponents(targetComponents);
      }

      Components configComponents = config.getComponents();

      // Add security schemes from config
      if (configComponents.getSecuritySchemes() != null) {
        if (targetComponents.getSecuritySchemes() == null) {
          targetComponents.setSecuritySchemes(new LinkedHashMap<>());
        }
        targetComponents.getSecuritySchemes().putAll(configComponents.getSecuritySchemes());
      }

      // Add callbacks from config
      if (configComponents.getCallbacks() != null) {
        if (targetComponents.getCallbacks() == null) {
          targetComponents.setCallbacks(new LinkedHashMap<>());
        }
        targetComponents.getCallbacks().putAll(configComponents.getCallbacks());
      }

      // Add examples from config
      if (configComponents.getExamples() != null) {
        if (targetComponents.getExamples() == null) {
          targetComponents.setExamples(new LinkedHashMap<>());
        }
        targetComponents.getExamples().putAll(configComponents.getExamples());
      }

      // Add headers from config
      if (configComponents.getHeaders() != null) {
        if (targetComponents.getHeaders() == null) {
          targetComponents.setHeaders(new LinkedHashMap<>());
        }
        targetComponents.getHeaders().putAll(configComponents.getHeaders());
      }

      // Add links from config
      if (configComponents.getLinks() != null) {
        if (targetComponents.getLinks() == null) {
          targetComponents.setLinks(new LinkedHashMap<>());
        }
        targetComponents.getLinks().putAll(configComponents.getLinks());
      }

      // Add parameters from config
      if (configComponents.getParameters() != null) {
        if (targetComponents.getParameters() == null) {
          targetComponents.setParameters(new LinkedHashMap<>());
        }
        targetComponents.getParameters().putAll(configComponents.getParameters());
      }

      // Add requestBodies from config
      if (configComponents.getRequestBodies() != null) {
        if (targetComponents.getRequestBodies() == null) {
          targetComponents.setRequestBodies(new LinkedHashMap<>());
        }
        targetComponents.getRequestBodies().putAll(configComponents.getRequestBodies());
      }

      // Add responses from config
      if (configComponents.getResponses() != null) {
        if (targetComponents.getResponses() == null) {
          targetComponents.setResponses(new LinkedHashMap<>());
        }
        targetComponents.getResponses().putAll(configComponents.getResponses());
      }

      // Merge schemas from config (config schemas override generated ones)
      if (configComponents.getSchemas() != null) {
        if (targetComponents.getSchemas() == null) {
          targetComponents.setSchemas(new LinkedHashMap<>());
        }
        targetComponents.getSchemas().putAll(configComponents.getSchemas());
      }
    }

    // Copy extensions from config
    if (config.getExtensions() != null) {
      for (Map.Entry<String, Object> entry : config.getExtensions().entrySet()) {
        target.addExtension(entry.getKey(), entry.getValue());
      }
    }
  }

  private static Info buildInfo(String packageName) {
    Info info = new Info();
    info.setTitle(packageName + " gRPC API");
    info.setVersion(API_VERSION);
    info.setDescription("OpenAPI specification generated from gRPC service definitions with HTTP transcoding");
    return info;
  }

  private static List<Server> buildServers() {
    Server server = new Server();
    server.setUrl(DEFAULT_SERVER_URL);
    server.setDescription("gRPC server");
    return Collections.singletonList(server);
  }

  private static Paths buildPaths(List<ServiceDescriptor> services) {
    Paths paths = new Paths();

    for (ServiceDescriptor service : services) {
      for (MethodDescriptor method : service.getTranscodingMethods()) {
        TranscodingDescriptor transcoding = method.getTranscoding();
        if (transcoding != null) {
          addPath(paths, service, method, transcoding, 0);

          // Handle additional bindings
          int bindingIndex = 1;
          for (TranscodingDescriptor additionalBinding : transcoding.getAdditionalBindings()) {
            addPath(paths, service, method, additionalBinding, bindingIndex++);
          }
        }
      }
    }

    return paths;
  }

  private static void addPath(Paths paths, ServiceDescriptor service, MethodDescriptor method,
    TranscodingDescriptor transcoding, int bindingIndex) {
    String path = expandSlashedPathPatterns(transcoding.getPath());
    String httpMethod = transcoding.getMethod().toLowerCase();

    PathItem pathItem = paths.get(path);
    if (pathItem == null) {
      pathItem = new PathItem();
      paths.addPathItem(path, pathItem);
    }

    Operation operation = buildOperation(service, method, transcoding, bindingIndex);

    switch (httpMethod) {
      case "get":
        pathItem.setGet(operation);
        break;
      case "post":
        pathItem.setPost(operation);
        break;
      case "put":
        pathItem.setPut(operation);
        break;
      case "delete":
        pathItem.setDelete(operation);
        break;
      case "patch":
        pathItem.setPatch(operation);
        break;
      default:
        pathItem.setPost(operation);
    }
  }

  /**
   * Expands slashed path parameters into standard OpenAPI path format. For example, paths with patterns like "name=projects/*" are expanded into standard OpenAPI path parameters
   * like "projects/[project]".
   *
   * @param path the original path with slashed patterns
   * @return the expanded path with standard OpenAPI parameters
   */
  private static String expandSlashedPathPatterns(String path) {
    if (path == null || !path.contains("=")) {
      return path;
    }

    Matcher matcher = SLASHED_PATH_PARAM_PATTERN.matcher(path);
    StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      String subPath = matcher.group(2); // e.g., "projects/*/messages/*" or "rooms/*/messages/*"
      String expanded = expandSubPath(subPath);
      matcher.appendReplacement(result, Matcher.quoteReplacement(expanded));
    }
    matcher.appendTail(result);

    return result.toString();
  }

  /**
   * Expands a sub-path pattern with wildcards into standard OpenAPI path parameters.
   */
  private static String expandSubPath(String subPath) {
    String[] segments = subPath.split("/");
    StringBuilder expanded = new StringBuilder();

    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        expanded.append("/");
      }

      String segment = segments[i];
      if ("*".equals(segment) || "**".equals(segment)) {
        // Find the previous segment to derive the parameter name
        String paramName = deriveParamName(segments, i);
        expanded.append("{").append(paramName).append("}");
      } else {
        expanded.append(segment);
      }
    }

    return expanded.toString();
  }

  /**
   * Derives a parameter name from the previous segment by trimming trailing "s".
   */
  private static String deriveParamName(String[] segments, int wildcardIndex) {
    if (wildcardIndex > 0) {
      String prevSegment = segments[wildcardIndex - 1];
      if (prevSegment.endsWith("s")) {
        return prevSegment.substring(0, prevSegment.length() - 1);
      }
      return prevSegment;
    }
    return "id" + wildcardIndex;
  }

  private static Operation buildOperation(ServiceDescriptor service, MethodDescriptor method,
    TranscodingDescriptor transcoding, int bindingIndex) {
    Operation operation = new Operation();
    operation.setSummary(method.getName());

    // Generate unique operationId: base name for index 0, append _httpMethod for additional bindings
    String operationId = method.getVertxMethodName();
    if (bindingIndex > 0) {
      operationId = operationId + "_" + transcoding.getMethod().toLowerCase();
    }
    operation.setOperationId(operationId);
    operation.setTags(Collections.singletonList(service.getName()));

    if (method.getDocumentation() != null && !method.getDocumentation().isEmpty()) {
      operation.setDescription(method.getDocumentation());
    }

    if (method.isDeprecated()) {
      operation.setDeprecated(true);
    }

    // Add request body if needed
    String body = transcoding.getBody();
    if (body != null && !body.isEmpty()) {
      operation.setRequestBody(buildRequestBody(method));
    }

    // Add responses
    operation.setResponses(buildResponses(method));

    return operation;
  }

  private static RequestBody buildRequestBody(MethodDescriptor method) {
    RequestBody requestBody = new RequestBody();
    requestBody.setRequired(true);

    Content content = new Content();
    MediaType mediaType = new MediaType();

    Schema<?> schema = new Schema<>();
    schema.set$ref("#/components/schemas/" + extractSimpleTypeName(method.getInputType()));
    mediaType.setSchema(schema);

    content.addMediaType(APPLICATION_JSON, mediaType);
    requestBody.setContent(content);

    return requestBody;
  }

  private static ApiResponses buildResponses(MethodDescriptor method) {
    ApiResponses responses = new ApiResponses();

    // Success response
    ApiResponse successResponse = new ApiResponse();
    successResponse.setDescription("Successful response");

    Content successContent = new Content();
    MediaType successMediaType = new MediaType();
    Schema<?> successSchema = new Schema<>();
    successSchema.set$ref("#/components/schemas/" + extractSimpleTypeName(method.getOutputType()));
    successMediaType.setSchema(successSchema);
    successContent.addMediaType(APPLICATION_JSON, successMediaType);
    successResponse.setContent(successContent);
    responses.addApiResponse("200", successResponse);

    // Error response
    ApiResponse errorResponse = new ApiResponse();
    errorResponse.setDescription("Error response");

    Content errorContent = new Content();
    MediaType errorMediaType = new MediaType();
    Schema<?> errorSchema = new Schema<>();
    errorSchema.set$ref("#/components/schemas/GrpcError");
    errorMediaType.setSchema(errorSchema);
    errorContent.addMediaType(APPLICATION_JSON, errorMediaType);
    errorResponse.setContent(errorContent);
    responses.addApiResponse("default", errorResponse);

    return responses;
  }

  private static Components buildComponents(List<ServiceDescriptor> services, Map<String, MessageDescriptor> descriptors) {
    Components components = new Components();
    Map<String, Schema> schemas = new LinkedHashMap<>();

    // Collect all message types used
    Set<String> types = collectAllTypes(services, descriptors);

    // Generate schemas for all collected types
    for (String type : types) {
      Schema<?> schema = buildSchema(type, descriptors);
      if (schema != null) {
        String schemaName = extractSimpleTypeName(type);
        schemas.put(schemaName, schema);
      }
    }

    // Add GrpcError schema
    schemas.put("GrpcError", buildGrpcErrorSchema());

    components.setSchemas(schemas);
    return components;
  }

  private static Set<String> collectAllTypes(List<ServiceDescriptor> services,
    Map<String, MessageDescriptor> messageDescriptors) {
    Set<String> allTypes = new LinkedHashSet<>();
    Set<String> processedTypes = new HashSet<>();
    Set<String> typesToProcess = new HashSet<>();

    // Collect initial types from methods
    for (ServiceDescriptor service : services) {
      for (MethodDescriptor method : service.getTranscodingMethods()) {
        typesToProcess.add(method.getInputType());
        typesToProcess.add(method.getOutputType());
      }
    }

    // Recursively collect nested message types
    while (!typesToProcess.isEmpty()) {
      String javaType = typesToProcess.iterator().next();
      typesToProcess.remove(javaType);

      if (processedTypes.contains(javaType)) {
        continue;
      }

      processedTypes.add(javaType);
      allTypes.add(javaType);

      // Find nested message types
      MessageDescriptor messageDesc = findMessageDescriptor(javaType, messageDescriptors);
      if (messageDesc != null) {
        for (MessageDescriptor.FieldDescriptor field : messageDesc.getFields()) {
          if (field.getType().startsWith(".")) {
            String nestedJavaType = field.getJavaType();
            if (!processedTypes.contains(nestedJavaType)) {
              typesToProcess.add(nestedJavaType);
            }
          }
        }
      }
    }

    return allTypes;
  }

  private static Schema<?> buildSchema(String javaType, Map<String, MessageDescriptor> messageDescriptors) {
    MessageDescriptor messageDesc = findMessageDescriptor(javaType, messageDescriptors);
    if (messageDesc == null) {
      return null;
    }

    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Protobuf message type: " + messageDesc.getFullName());

    Map<String, Schema> properties = new LinkedHashMap<>();
    for (MessageDescriptor.FieldDescriptor field : messageDesc.getFields()) {
      Schema<?> fieldSchema = buildFieldSchema(field);
      properties.put(field.getJsonName(), fieldSchema);
    }
    schema.setProperties(properties);

    return schema;
  }

  private static Schema<?> buildFieldSchema(MessageDescriptor.FieldDescriptor field) {
    Schema<?> baseSchema;

    if (field.getType().startsWith(".")) {
      // Reference to another message type
      Schema<?> refSchema = new Schema<>();
      refSchema.set$ref("#/components/schemas/" + extractSimpleTypeName(field.getJavaType()));
      baseSchema = refSchema;
    } else {
      // Primitive type
      baseSchema = mapToOpenApiSchema(field.getType());
    }

    if (field.isRepeated()) {
      ArraySchema arraySchema = new ArraySchema();
      arraySchema.setItems(baseSchema);
      if (field.getDocumentation() != null && !field.getDocumentation().isEmpty()) {
        arraySchema.setDescription(field.getDocumentation());
      }
      return arraySchema;
    }

    if (field.getDocumentation() != null && !field.getDocumentation().isEmpty()) {
      baseSchema.setDescription(field.getDocumentation());
    }

    return baseSchema;
  }

  private static Schema<?> mapToOpenApiSchema(String protobufType) {
    switch (protobufType) {
      case "string":
        return new StringSchema();
      case "int32":
      case "sint32":
      case "sfixed32":
        IntegerSchema int32Schema = new IntegerSchema();
        int32Schema.setFormat("int32");
        return int32Schema;
      case "int64":
      case "sint64":
      case "sfixed64":
        IntegerSchema int64Schema = new IntegerSchema();
        int64Schema.setFormat("int64");
        return int64Schema;
      case "uint32":
      case "fixed32":
        IntegerSchema uint32Schema = new IntegerSchema();
        uint32Schema.setFormat("int32");
        return uint32Schema;
      case "uint64":
      case "fixed64":
        // uint64 is represented as string in JSON to preserve precision
        StringSchema uint64Schema = new StringSchema();
        uint64Schema.setFormat("uint64");
        return uint64Schema;
      case "double":
        NumberSchema doubleSchema = new NumberSchema();
        doubleSchema.setFormat("double");
        return doubleSchema;
      case "float":
        NumberSchema floatSchema = new NumberSchema();
        floatSchema.setFormat("float");
        return floatSchema;
      case "bool":
        return new BooleanSchema();
      case "bytes":
        StringSchema bytesSchema = new StringSchema();
        bytesSchema.setFormat("byte");
        return bytesSchema;
      default:
        return new StringSchema();
    }
  }

  private static Schema<?> buildGrpcErrorSchema() {
    ObjectSchema schema = new ObjectSchema();
    Map<String, Schema> properties = new LinkedHashMap<>();

    IntegerSchema codeSchema = new IntegerSchema();
    codeSchema.setDescription("gRPC status code");
    properties.put("code", codeSchema);

    StringSchema messageSchema = new StringSchema();
    messageSchema.setDescription("Error message");
    properties.put("message", messageSchema);

    ArraySchema detailsSchema = new ArraySchema();
    detailsSchema.setItems(new ObjectSchema());
    detailsSchema.setDescription("Additional error details");
    properties.put("details", detailsSchema);

    schema.setProperties(properties);
    return schema;
  }

  private static MessageDescriptor findMessageDescriptor(String type, Map<String, MessageDescriptor> descriptors) {
    for (MessageDescriptor desc : descriptors.values()) {
      if (desc.getJavaType().equals(type)) {
        return desc;
      }
    }
    return null;
  }

  private static String extractSimpleTypeName(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    int lastDot = name.lastIndexOf('.');
    return lastDot >= 0 ? name.substring(lastDot + 1) : name;
  }
}
