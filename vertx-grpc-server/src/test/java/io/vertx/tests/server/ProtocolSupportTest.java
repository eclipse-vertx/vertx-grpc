/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.server;

import io.vertx.core.http.*;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import org.junit.Test;

public class ProtocolSupportTest extends ServerTestBase {

  private HttpClient client;

  @Test
  public void testDisableHTTP2(TestContext should) {
    testDisableProtocol(should, "application/grpc", GrpcProtocol.HTTP_2);
  }

  @Test
  public void testDisableWeb(TestContext should) {
    testDisableProtocol(should, "application/grpc-web", GrpcProtocol.WEB);
  }

  private void testDisableProtocol(TestContext should, String contentType, GrpcProtocol protocol) {

    startServer(GrpcServer.server(vertx, new GrpcServerOptions().removeEnabledProtocol(protocol)));

    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(true)
    );

    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(request -> {
        request.putHeader(GrpcHeaderNames.GRPC_ENCODING, "identity");
        request.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
        request.send();
        return request.response()
          .map(resp -> resp.statusCode());
      }).onComplete(should.asyncAssertSuccess(status -> {
        should.assertEquals(415, status);
      }));
  }
}
