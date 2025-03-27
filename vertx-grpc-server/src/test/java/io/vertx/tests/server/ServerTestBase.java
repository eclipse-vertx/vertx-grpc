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

import io.grpc.ManagedChannel;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.Empty;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestConstants;
import junit.framework.AssertionFailedError;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public abstract class ServerTestBase extends GrpcTestBase {

  public static final ServiceMethod<Request, Reply> UNARY = ServiceMethod.server(TestConstants.TEST_SERVICE, "Unary", TestConstants.REPLY_ENC, TestConstants.REQUEST_DEC);
  public static final ServiceMethod<JsonObject, JsonObject> UNARY_JSON = ServiceMethod.server(TestConstants.TEST_SERVICE, "Unary", GrpcMessageEncoder.JSON_OBJECT, GrpcMessageDecoder.JSON_OBJECT);
  public static final ServiceMethod<Empty, Reply> SOURCE = ServiceMethod.server(TestConstants.TEST_SERVICE, "Source", TestConstants.REPLY_ENC, TestConstants.EMPTY_DEC);
  public static final ServiceMethod<Request, Empty> SINK = ServiceMethod.server(TestConstants.TEST_SERVICE, "Sink", TestConstants.EMPTY_ENC, TestConstants.REQUEST_DEC);
  public static final ServiceMethod<Request, Reply> PIPE = ServiceMethod.server(TestConstants.TEST_SERVICE, "Pipe", TestConstants.REPLY_ENC, TestConstants.REQUEST_DEC);

  protected volatile ManagedChannel channel;

  @Override
  public void tearDown(TestContext should) {
    if (channel != null) {
      channel.shutdown();
    }
    super.tearDown(should);
  }

  protected HttpServer createServer(HttpServerOptions options, GrpcServer server) {
    return vertx.createHttpServer(options)
      .requestHandler(server);
  }

  protected HttpServer createServer(GrpcServer server) {
    return vertx.createHttpServer(new HttpServerOptions().setPort(8080).setHost("localhost"))
      .requestHandler(server);
  }

  protected void startServer(GrpcServer server) {
    startServer(new HttpServerOptions().setPort(8080).setHost("localhost"), server);
  }

  protected void startServer(HttpServerOptions options, GrpcServer server) {
    HttpServer httpServer = createServer(options, server);
    try {
      httpServer
        .listen()
        .await(20, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(e);
      throw afe;
    }
  }
}
