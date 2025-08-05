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
package io.vertx.tests.server;

import com.google.protobuf.Descriptors;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.tests.common.grpc.TestConstants.TEST_SERVICE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LifecycleTest extends ServerTestBase {

  @Test
  public void testCloseService() throws Exception {
    AtomicInteger closed = new AtomicInteger();
    GrpcServer server = GrpcServer
      .server(vertx)
      .addService(new Service() {
        @Override
        public ServiceName name() {
          return TEST_SERVICE;
        }
        @Override
        public Descriptors.ServiceDescriptor descriptor() {
          return null;
        }
        @Override
        public void bind(GrpcServer server) {
        }
        @Override
        public Future<Void> close() {
          closed.incrementAndGet();
          return Service.super.close().timeout(10, TimeUnit.MILLISECONDS);
        }
      });
    HttpServer httpServer = createServer(server);
    httpServer.listen().await();
    httpServer.close().await();
    assertEquals(1, closed.get());
  }

  @Test
  public void testRegisterAfterClose() throws Exception {
    GrpcServer server = GrpcServer.server(vertx);
    HttpServer httpServer = createServer(server);
    httpServer.listen().await();
    httpServer.close().await();
    try {
      server.addService(new Service() {
        @Override
        public ServiceName name() {
          return TEST_SERVICE;
        }
        @Override
        public Descriptors.ServiceDescriptor descriptor() {
          return null;
        }
        @Override
        public void bind(GrpcServer server) {
        }
      });
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      server.callHandler(req -> {});
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      server.callHandler(UNARY, req -> {});
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
