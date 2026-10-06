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
package io.vertx.grpc.server.tests;

import io.vertx.core.http.*;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import org.junit.Test;

public class HttpMethodValidationTest extends ServerTestBase {

  private HttpClient client;

  @Test
  public void testGetGrpc(TestContext should) {
    testNonPostMethod(should, HttpMethod.GET, "application/grpc", HttpVersion.HTTP_2);
  }

  @Test
  public void testPutGrpc(TestContext should) {
    testNonPostMethod(should, HttpMethod.PUT, "application/grpc", HttpVersion.HTTP_2);
  }

  @Test
  public void testDeleteGrpc(TestContext should) {
    testNonPostMethod(should, HttpMethod.DELETE, "application/grpc", HttpVersion.HTTP_2);
  }

  @Test
  public void testPatchGrpc(TestContext should) {
    testNonPostMethod(should, HttpMethod.PATCH, "application/grpc", HttpVersion.HTTP_2);
  }

  @Test
  public void testGetGrpcWeb(TestContext should) {
    testNonPostMethod(should, HttpMethod.GET, "application/grpc-web", HttpVersion.HTTP_1_1);
  }

  @Test
  public void testPutGrpcWeb(TestContext should) {
    testNonPostMethod(should, HttpMethod.PUT, "application/grpc-web", HttpVersion.HTTP_1_1);
  }

  @Test
  public void testGetGrpcWebText(TestContext should) {
    testNonPostMethod(should, HttpMethod.GET, "application/grpc-web-text", HttpVersion.HTTP_1_1);
  }

  @Test
  public void testPutGrpcWebText(TestContext should) {
    testNonPostMethod(should, HttpMethod.PUT, "application/grpc-web-text", HttpVersion.HTTP_1_1);
  }

  private void testNonPostMethod(TestContext should, HttpMethod method, String contentType, HttpVersion version) {

    startServer(GrpcServer.server(vertx, new GrpcServerOptions()));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(version)
      .setHttp2ClearTextUpgrade(true)
    );

    client
      .request(method, 8080, "localhost", "/")
      .compose(request -> {
        request.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
        request.send();
        return request.response();
      }).onComplete(should.asyncAssertSuccess(resp -> {
        should.assertEquals(405, resp.statusCode());
        should.assertEquals("POST", resp.getHeader("Allow"));
      }));
  }
}
