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
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.Empty;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestConstants;
import org.junit.After;
import org.junit.runner.RunWith;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public abstract class ClientTestBase extends GrpcTestBase {

  public static final ServiceMethod<Reply, Request> UNARY = ServiceMethod.client(TestConstants.TEST_SERVICE, "Unary", TestConstants.REQUEST_ENC, TestConstants.REPLY_DEC);
  public static final ServiceMethod<Reply, Empty> SOURCE = ServiceMethod.client(TestConstants.TEST_SERVICE, "Source", TestConstants.EMPTY_ENC, TestConstants.REPLY_DEC);
  public static final ServiceMethod<Empty, Request> SINK = ServiceMethod.client(TestConstants.TEST_SERVICE, "Sink", TestConstants.REQUEST_ENC, TestConstants.EMPTY_DEC);
  public static final ServiceMethod<Reply, Request> PIPE = ServiceMethod.client(TestConstants.TEST_SERVICE, "Pipe", TestConstants.REQUEST_ENC, TestConstants.REPLY_DEC);

  /* The port on which the server should run */
  private List<Server> servers = new ArrayList<>();

  @After
  public void tearDown(TestContext should) {
    stopServers(false);
    super.tearDown(should);
  }

  void startServer(BindableService service) throws IOException {
    startServer(service, ServerBuilder.forPort(port));
  }

  void stopServers(boolean now) {

    List<Server> list = new ArrayList<>(servers);
    servers.clear();
    list.forEach(server -> {
      if (now) {
        server.shutdownNow();
      } else {
        server.shutdown();
      }
    });
  }

  void startServer(BindableService service, ServerBuilder<?> builder) throws IOException {
    servers.add(builder
      .addService(service)
      .build()
      .start());
  }


  void startServer(ServerServiceDefinition service) throws IOException {
    startServer(service, ServerBuilder.forPort(port));
  }

  void startServer(ServerServiceDefinition service, ServerBuilder<?> builder) throws IOException {
    servers.add(builder
      .addService(service)
      .build()
      .start());
  }
}
