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
package io.vertx.grpc.server.tests;

import io.vertx.grpc.server.impl.GrpcMethodCall;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GrpcMethodCallTest {

  private GrpcMethodCall<?, ?> grpcMethodCall0;
  private GrpcMethodCall<?, ?> grpcMethodCall1;
  private GrpcMethodCall<?, ?> grpcMethodCall2;

  @Before
  public void setUp() {
    grpcMethodCall0 = new GrpcMethodCall<>("/com.examples.MyService/Method1", null, null, null);
    grpcMethodCall1 = new GrpcMethodCall<>("/com.examples/MyService/Method2", null, null, null);
    grpcMethodCall2 = new GrpcMethodCall<>("/MyService/Method3", null, null, null);
  }

  @Test
  public void testFullMethodName() {
    assertEquals("com.examples.MyService/Method1", grpcMethodCall0.fullMethodName());
    assertEquals("com.examples/MyService/Method2", grpcMethodCall1.fullMethodName());
    assertEquals("MyService/Method3", grpcMethodCall2.fullMethodName());
  }

  @Test
  public void testServiceName() {
    assertEquals("com.examples.MyService", grpcMethodCall0.serviceName().fullyQualifiedName());
    assertNull(grpcMethodCall1.serviceName());
    assertEquals("MyService", grpcMethodCall2.serviceName().fullyQualifiedName());
  }

  @Test
  public void testMethodName() {
    assertEquals("Method1", grpcMethodCall0.methodName());
    assertNull(grpcMethodCall1.methodName());
    assertEquals("Method3", grpcMethodCall2.methodName());
  }
}
