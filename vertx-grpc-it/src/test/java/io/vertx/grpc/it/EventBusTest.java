/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.it;

import io.grpc.examples.helloworld.*;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.tests.common.GrpcTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventBusTest extends GrpcTestBase {

  private static final HelloRequest HELLO_REQUEST = HelloRequest.newBuilder()
    .setName("Julien")
    .build();

  private static final HelloRequest HELLO_EMPTY_REQUEST = HelloRequest.getDefaultInstance();

  private void registerGreeterHandler(GreeterService service) {
    new GreeterEventBusHandler(service).register(vertx.eventBus());
  }

  @Test
  public void testUnaryCall(TestContext should) throws Exception {
    registerGreeterHandler(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    });

    GreeterEventBusProxy proxy = new GreeterEventBusProxy(vertx);
    HelloReply reply = proxy.sayHello(HELLO_REQUEST).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testMultipleMethods(TestContext should) throws Exception {
    registerGreeterHandler(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
      @Override
      public Future<HelloReply> sayHelloAgain(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello again " + request.getName())
          .build());
      }
    });

    GreeterEventBusProxy proxy = new GreeterEventBusProxy(vertx);

    HelloReply reply1 = proxy.sayHello(HELLO_REQUEST).await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", reply1.getMessage());

    HelloReply reply2 = proxy.sayHelloAgain(HELLO_REQUEST).await(10, TimeUnit.SECONDS);
    assertEquals("Hello again Julien", reply2.getMessage());
  }

  @Test
  public void testServiceFailure(TestContext should) throws Exception {
    registerGreeterHandler(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.failedFuture("Something went wrong");
      }
    });

    GreeterEventBusProxy proxy = new GreeterEventBusProxy(vertx);
    try {
      proxy.sayHello(HELLO_REQUEST).await(10, TimeUnit.SECONDS);
      should.fail("Should have thrown");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Something went wrong"));
    }
  }

  @Test
  public void testEmptyRequest(TestContext should) throws Exception {
    registerGreeterHandler(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        String name = request.getName().isEmpty() ? "Anonymous" : request.getName();
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + name)
          .build());
      }
    });

    GreeterEventBusProxy proxy = new GreeterEventBusProxy(vertx);
    HelloReply reply = proxy.sayHello(HELLO_EMPTY_REQUEST).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Anonymous", reply.getMessage());
  }

  @Test
  public void testWithDeliveryOptions(TestContext should) throws Exception {
    registerGreeterHandler(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder()
          .setMessage("Hello " + request.getName())
          .build());
      }
    });

    DeliveryOptions options = new DeliveryOptions().setSendTimeout(5000);
    GreeterEventBusProxy proxy = new GreeterEventBusProxy(vertx, options);
    HelloReply reply = proxy.sayHello(HELLO_REQUEST).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }
}
