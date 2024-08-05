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
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.streaming.Empty;
import io.grpc.examples.streaming.Item;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.tests.common.GrpcTestBase;
import junit.framework.AssertionFailedError;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.vertx.grpc.common.GrpcMessageDecoder.decoder;
import static io.vertx.grpc.common.GrpcMessageEncoder.encoder;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public abstract class ServerTestBase extends GrpcTestBase {

  public static final ServiceName GREETER = ServiceName.create("helloworld.Greeter");
  public static final ServiceName STREAMING = ServiceName.create("streaming.Streaming");

  public static final GrpcMessageEncoder<Empty> EMPTY_ENC = encoder();
  public static final GrpcMessageDecoder<Empty> EMPTY_DEC = decoder(Empty.parser());
  public static final GrpcMessageEncoder<Item> ITEM_ENC = encoder();
  public static final GrpcMessageDecoder<Item> ITEM_DEC = decoder(Item.parser());
  public static final GrpcMessageEncoder<HelloRequest> HELLO_REQUEST_ENC = encoder();
  public static final GrpcMessageDecoder<HelloRequest> HELLO_REQUEST_DEC = decoder(HelloRequest.parser());
  public static final GrpcMessageEncoder<HelloReply> HELLO_REPLY_ENC = encoder();
  public static final GrpcMessageDecoder<HelloReply> HELLO_REPLY_DEC = decoder(HelloReply.parser());

  public static final ServiceMethod<HelloRequest, HelloReply> GREETER_SAY_HELLO = ServiceMethod.server(GREETER, "SayHello", HELLO_REPLY_ENC, HELLO_REQUEST_DEC);
  public static final ServiceMethod<JsonObject, JsonObject> GREETER_SAY_HELLO_JSON = ServiceMethod.server(GREETER, "SayHello", GrpcMessageEncoder.JSON_OBJECT, GrpcMessageDecoder.JSON_OBJECT);
  public static final ServiceMethod<Empty, Item> STREAMING_SOURCE = ServiceMethod.server(STREAMING, "Source", ITEM_ENC, EMPTY_DEC);
  public static final ServiceMethod<Item, Empty> STREAMING_SINK = ServiceMethod.server(STREAMING, "Sink", EMPTY_ENC, ITEM_DEC);
  public static final ServiceMethod<Item, Item> STREAMING_PIPE = ServiceMethod.server(STREAMING, "Pipe", ITEM_ENC, ITEM_DEC);

  protected volatile ManagedChannel channel;

  @Override
  public void tearDown(TestContext should) {
    if (channel != null) {
      channel.shutdown();
    }
    super.tearDown(should);
  }

  protected void startServer(GrpcServer server) {
    startServer(new HttpServerOptions().setPort(8080).setHost("localhost"), server);
  }

  protected void startServer(HttpServerOptions options, GrpcServer server) {
    CompletableFuture<Void> res = new CompletableFuture<>();
    vertx.createHttpServer(options).requestHandler(server).listen()
      .onComplete(ar -> {
        if (ar.succeeded()) {
          res.complete(null);
        } else {
          res.completeExceptionally(ar.cause());
        }
      });
    try {
      res.get(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(e);
      throw afe;
    } catch (ExecutionException e) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(e.getCause());
      throw afe;
    } catch (TimeoutException e) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(e);
      throw afe;
    }
  }
}
