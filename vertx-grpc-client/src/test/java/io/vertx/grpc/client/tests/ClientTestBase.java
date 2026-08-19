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
package io.vertx.grpc.client.tests;

import io.grpc.*;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.tests.GrpcTestBase;
import io.vertx.grpc.common.tests.Empty;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.common.tests.TestConstants;
import org.junit.After;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
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

  protected void startServer(BindableService service) throws IOException {
    startServer(service, ServerBuilder.forPort(port));
  }

  protected void stopServers(boolean now) {

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

  protected void startServer(BindableService service, ServerBuilder<?> builder) throws IOException {
    servers.add(builder
      .addService(service)
      .build()
      .start());
  }


  protected void startServer(ServerServiceDefinition service) throws IOException {
    startServer(service, ServerBuilder.forPort(port));
  }

  protected void startServer(ServerServiceDefinition service, ServerBuilder<?> builder) throws IOException {
    servers.add(builder
      .addService(service)
      .build()
      .start());
  }
}
