/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.it;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.openapi.contract.OpenAPIContract;
import io.vertx.openapi.contract.Operation;
import io.vertx.openapi.contract.Path;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Integration tests for OpenAPI generation through the protoc plugin. Validates that generated OpenAPI specifications are valid and contain the expected paths, operations, and
 * schemas from proto files with HTTP transcoding annotations.
 */
@RunWith(VertxUnitRunner.class)
public class SchemaGenerationTest extends GrpcTestBase {

  private static final String GENERATED_SOURCES_PATH = "target/generated-test-sources/protobuf/";
  private static final String OPENAPI_JSON = GENERATED_SOURCES_PATH + "openapi.json";
  private static final String OPENAPI_YAML = GENERATED_SOURCES_PATH + "openapi.yaml";

  @Test
  public void testOpenApiJsonFileExists() {
    File jsonFile = new File(OPENAPI_JSON);
    assertTrue("openapi.json should exist in generated sources", jsonFile.exists());
    assertTrue("openapi.json should not be empty", jsonFile.length() > 0);
  }

  @Test
  public void testOpenApiYamlFileExists() {
    File yamlFile = new File(OPENAPI_YAML);
    assertTrue("openapi.yaml should exist in generated sources", yamlFile.exists());
    assertTrue("openapi.yaml should not be empty", yamlFile.length() > 0);
  }

  @Test
  public void testOpenApiJsonIsValid() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON)
      .expecting(Objects::nonNull)
      .await(20, TimeUnit.SECONDS);
    assertNotNull("Contract should be parsed successfully", contract);
  }

  @Test
  public void testOpenApiYamlIsValid() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_YAML)
      .expecting(Objects::nonNull)
      .await(20, TimeUnit.SECONDS);
    assertNotNull("Contract should be parsed successfully", contract);
  }

  @Test
  public void testOpenApiContainsPaths() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);

    List<Path> paths = contract.getPaths();
    assertFalse("Contract should contain paths", paths.isEmpty());

    Set<String> pathStrings = paths.stream()
      .map(Path::getName)
      .collect(Collectors.toSet());

    // Expected paths from helloworld.proto HTTP annotations
    Set<String> expectedPaths = new HashSet<>(Arrays.asList(
      "/v1/hello/{name}",
      "/v1/hello",
      "/v2/hello",
      "/v2/hello/{name}",
      "/v1/hello/custom/{name}",
      "/v1/rooms/{room}/messages/{message}",
      "/v1/hello/body",
      "/v1/hello/body/response"
    ));

    for (String expectedPath : expectedPaths) {
      assertTrue("Contract should contain path: " + expectedPath, pathStrings.contains(expectedPath));
    }
  }

  @Test
  public void testOpenApiContainsOperations() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);

    List<Operation> operations = contract.operations();
    assertFalse("Contract should contain operations", operations.isEmpty());

    Set<String> operationIds = operations.stream()
      .map(Operation::getOperationId)
      .collect(Collectors.toSet());

    // Expected operation IDs from helloworld.proto
    Set<String> expectedOperationIds = new HashSet<>(Arrays.asList(
      "sayHello",
      "sayHello_post", // additional binding for POST /v1/hello
      "sayHelloAgain",
      "sayHelloAgain_get", // additional binding for GET /v2/hello/{name}
      "sayHelloCustom",
      "sayHelloNested",
      "sayHelloWithBody",
      "sayHelloWithResponseBOdy"
    ));

    for (String expectedOpId : expectedOperationIds) {
      assertTrue("Contract should contain operation: " + expectedOpId, operationIds.contains(expectedOpId));
    }
  }

  @Test
  public void testOpenApiOperationsByPath() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);

    // Verify GET /v1/hello/{name} - primary binding
    Operation getHello = contract.findOperation("/v1/hello/test", HttpMethod.GET);
    assertNotNull("GET /v1/hello/{name} should exist", getHello);
    assertEquals("sayHello", getHello.getOperationId());

    // Verify POST /v1/hello - additional binding (gets _post suffix)
    Operation postHello = contract.findOperation("/v1/hello", HttpMethod.POST);
    assertNotNull("POST /v1/hello should exist", postHello);
    assertEquals("sayHello_post", postHello.getOperationId());

    // Verify POST /v2/hello - primary binding
    Operation postHelloAgain = contract.findOperation("/v2/hello", HttpMethod.POST);
    assertNotNull("POST /v2/hello should exist", postHelloAgain);
    assertEquals("sayHelloAgain", postHelloAgain.getOperationId());

    // Verify GET /v2/hello/{name} - additional binding (gets _get suffix)
    Operation getHelloAgain = contract.findOperation("/v2/hello/world", HttpMethod.GET);
    assertNotNull("GET /v2/hello/{name} should exist", getHelloAgain);
    assertEquals("sayHelloAgain_get", getHelloAgain.getOperationId());

    // Verify POST /v1/hello/body (with body annotation)
    Operation postHelloWithBody = contract.findOperation("/v1/hello/body", HttpMethod.POST);
    assertNotNull("POST /v1/hello/body should exist", postHelloWithBody);
    assertEquals("sayHelloWithBody", postHelloWithBody.getOperationId());
  }

  @Test
  public void testOpenApiContainsSchemas() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);

    List<Operation> operations = contract.operations();
    assertFalse("Contract should have operations referencing schemas", operations.isEmpty());
  }

  @Test
  public void testOpenApiJsonAndYamlEquivalent() throws Exception {
    OpenAPIContract jsonContract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);
    OpenAPIContract yamlContract = OpenAPIContract.from(vertx, OPENAPI_YAML).await(20, TimeUnit.SECONDS);

    // Both should have the same paths
    assertEquals("JSON and YAML should have same number of paths",
      jsonContract.getPaths().size(),
      yamlContract.getPaths().size()
    );

    // Both should have the same operations
    assertEquals("JSON and YAML should have same number of operations",
      jsonContract.operations().size(),
      yamlContract.operations().size()
    );

    // Verify same path names
    Set<String> jsonPaths = jsonContract.getPaths().stream().map(Path::getName).collect(Collectors.toSet());
    Set<String> yamlPaths = yamlContract.getPaths().stream().map(Path::getName).collect(Collectors.toSet());

    assertEquals("JSON and YAML should have identical paths", jsonPaths, yamlPaths);
  }

  @Test
  public void testOpenApiOperationsHaveGreeterTag() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);

    List<Operation> operations = contract.operations();

    for (Operation op : operations) {
      List<String> tags = op.getTags();
      assertNotNull("Operation " + op.getOperationId() + " should have tags", tags);
      assertTrue("Operation " + op.getOperationId() + " should have Greeter tag", tags.contains("Greeter"));
    }
  }

  @Test
  public void testOpenApiOperationById() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);

    // Get operations by their operation ID
    Operation sayHello = contract.operation("sayHello");
    assertNotNull("Operation 'sayHello' should exist", sayHello);

    Operation sayHelloAgain = contract.operation("sayHelloAgain");
    assertNotNull("Operation 'sayHelloAgain' should exist", sayHelloAgain);

    Operation sayHelloWithBody = contract.operation("sayHelloWithBody");
    assertNotNull("Operation 'sayHelloWithBody' should exist", sayHelloWithBody);
  }

  @Test
  public void testOpenApiRawContractStructure() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);

    // Verify raw contract has expected structure
    JsonObject rawContract = contract.getRawContract();
    assertNotNull("Raw contract should not be null", rawContract);

    // Verify openapi version
    assertEquals("OpenAPI version should be 3.0.0", "3.0.0", rawContract.getString("openapi"));

    // Verify paths section exists
    JsonObject paths = rawContract.getJsonObject("paths");
    assertNotNull("Paths section should exist", paths);
    assertFalse("Paths should not be empty", paths.isEmpty());

    // Verify components/schemas section exists
    JsonObject components = rawContract.getJsonObject("components");
    assertNotNull("Components section should exist", components);
    JsonObject schemas = components.getJsonObject("schemas");
    assertNotNull("Schemas section should exist", schemas);

    // Verify expected schemas exist
    assertTrue("HelloRequest schema should exist", schemas.containsKey("HelloRequest"));
    assertTrue("HelloReply schema should exist", schemas.containsKey("HelloReply"));
    assertTrue("GrpcError schema should exist", schemas.containsKey("GrpcError"));
  }

  @Test
  public void testOpenApiConfigFileApplied() throws Exception {
    OpenAPIContract contract = OpenAPIContract.from(vertx, OPENAPI_JSON).await(20, TimeUnit.SECONDS);
    JsonObject rawContract = contract.getRawContract();

    // Verify info section from config file
    JsonObject info = rawContract.getJsonObject("info");
    assertNotNull("Info section should exist", info);
    assertEquals("Title should be from config", "Custom Greeting API", info.getString("title"));
    assertEquals("Version should be from config", "2.0.0", info.getString("version"));
    assertEquals("Description should be from config",
      "A custom greeting service with full configuration", info.getString("description"));

    // Verify contact from config
    JsonObject contact = info.getJsonObject("contact");
    assertNotNull("Contact should exist from config", contact);
    assertEquals("Test Support", contact.getString("name"));
    assertEquals("test@example.com", contact.getString("email"));

    // Verify servers from config
    var servers = rawContract.getJsonArray("servers");
    assertNotNull("Servers should exist from config", servers);
    assertEquals("Should have 2 servers from config", 2, servers.size());
    assertEquals("https://api.example.com", servers.getJsonObject(0).getString("url"));
    assertEquals("https://staging.example.com", servers.getJsonObject(1).getString("url"));

    // Verify security from config
    var security = rawContract.getJsonArray("security");
    assertNotNull("Security should exist from config", security);
    assertFalse("Security should not be empty", security.isEmpty());

    // Verify externalDocs from config
    JsonObject externalDocs = rawContract.getJsonObject("externalDocs");
    assertNotNull("ExternalDocs should exist from config", externalDocs);
    assertEquals("External documentation", externalDocs.getString("description"));
    assertEquals("https://docs.example.com", externalDocs.getString("url"));

    // Verify tags from config
    var tags = rawContract.getJsonArray("tags");
    assertNotNull("Tags should exist from config", tags);
    assertEquals(1, tags.size());
    assertEquals("Greeter", tags.getJsonObject(0).getString("name"));
    assertEquals("Greeting operations for testing", tags.getJsonObject(0).getString("description"));

    // Verify securitySchemes from config were merged into components
    JsonObject securitySchemes = rawContract.getJsonObject("components").getJsonObject("securitySchemes");
    assertNotNull("SecuritySchemes should exist from config", securitySchemes);
    assertTrue("bearerAuth scheme should exist", securitySchemes.containsKey("bearerAuth"));
  }
}
