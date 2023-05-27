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
package io.vertx.grpc.common;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ServiceNameTest {
  ServiceName sn0;
  ServiceName sn1;
  ServiceName sn2;
  ServiceName sn3;
  ServiceName sn4;

  String method0 = "Method0";
  String method1 = "Method1";

  @Before
  public void setUp() throws Exception {
    sn0 = ServiceName.create("com.examples.MyService");
    sn1 = ServiceName.create("com.examples", "MyService");
    sn2 = ServiceName.create("MyService");
    sn3 = ServiceName.create("", "MyService");
    sn4 = ServiceName.create(null, "MyService");
  }

  @Test
  public void testServiceName() {
    for (ServiceName sn : Arrays.asList(ServiceName.create("com.examples.MyService"), ServiceName.create("com.examples", "MyService"))) {
      assertEquals("com.examples", sn.packageName());
      assertEquals("MyService", sn.name());
      assertEquals("com.examples.MyService", sn.fullyQualifiedName());
    }
  }

  @Test
  public void name() {
    assertEquals("MyService", sn0.name());
    assertEquals("MyService", sn1.name());
    assertEquals("MyService", sn2.name());
    assertEquals("MyService", sn3.name());
    assertEquals("MyService", sn4.name());
  }

  @Test
  public void packageName() {
    assertEquals("com.examples", sn0.packageName());
    assertEquals("com.examples", sn1.packageName());
    assertEquals("", sn2.packageName());
    assertEquals("", sn3.packageName());
    assertEquals("", sn4.packageName());
  }

  @Test
  public void fullyQualifiedName() {
    assertEquals("com.examples.MyService", sn0.fullyQualifiedName());
    assertEquals("com.examples.MyService", sn1.fullyQualifiedName());
    assertEquals("MyService", sn2.fullyQualifiedName());
    assertEquals("MyService", sn3.fullyQualifiedName());
    assertEquals("MyService", sn4.fullyQualifiedName());
  }

  @Test
  public void pathOf() {

    assertEquals("/com.examples.MyService/Method0", sn0.pathOf(method0));
      assertEquals("/com.examples.MyService/Method0", sn1.pathOf(method0));
      assertEquals("/MyService/Method0", sn2.pathOf(method0));
      assertEquals("/MyService/Method0", sn3.pathOf(method0));
      assertEquals("/MyService/Method0", sn4.pathOf(method0));
  }
}
