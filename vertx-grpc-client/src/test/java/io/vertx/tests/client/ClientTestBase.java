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
package io.vertx.tests.client;

import io.grpc.*;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.streaming.Empty;
import io.grpc.examples.streaming.Item;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.*;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.After;
import org.junit.runner.RunWith;

import java.io.IOException;

import static io.vertx.grpc.common.GrpcMessageDecoder.decoder;
import static io.vertx.grpc.common.GrpcMessageEncoder.encoder;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public abstract class ClientTestBase extends GrpcTestBase {

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

  public static final ServiceMethod<HelloReply, HelloRequest> GREETER_SAY_HELLO = ServiceMethod.client(GREETER, "SayHello", HELLO_REQUEST_ENC, HELLO_REPLY_DEC);
  public static final ServiceMethod<Item, Empty> STREAMING_SOURCE = ServiceMethod.client(STREAMING, "Source", EMPTY_ENC, ITEM_DEC);
  public static final ServiceMethod<Empty, Item> STREAMING_SINK = ServiceMethod.client(STREAMING, "Sink", ITEM_ENC, EMPTY_DEC);
  public static final ServiceMethod<Item, Item> STREAMING_PIPE = ServiceMethod.client(STREAMING, "Pipe", ITEM_ENC, ITEM_DEC);

  /* The port on which the server should run */
  protected Server server;

  @After
  public void tearDown(TestContext should) {
    stopServer(false);
    super.tearDown(should);
  }

  void startServer(BindableService service) throws IOException {
    startServer(service, ServerBuilder.forPort(port));
  }

  void stopServer(boolean now) {
    Server s = server;
    if (s != null) {
      server = null;
      if (now) {
        s.shutdownNow();
      } else {
        s.shutdown();
      }
    }
  }

  void startServer(BindableService service, ServerBuilder builder) throws IOException {
    server = builder
        .addService(service)
        .build()
        .start();
  }


  void startServer(ServerServiceDefinition service) throws IOException {
    startServer(service, ServerBuilder.forPort(port));
  }

  void startServer(ServerServiceDefinition service, ServerBuilder builder) throws IOException {
    server = builder
      .addService(service)
      .build()
      .start();
  }
}
