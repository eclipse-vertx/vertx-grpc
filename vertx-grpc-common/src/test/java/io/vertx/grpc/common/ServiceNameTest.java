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
import static org.junit.Assert.assertNotEquals;

public class ServiceNameTest {
  ServiceName sn0;
  ServiceName sn1;

  String method0 = "Method0";

  @Before
  public void setUp() {
    sn0 = ServiceName.create("com.examples.MyService");
    sn1 = ServiceName.create("com.examples", "MyService");
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
  }

  @Test
  public void packageName() {
    assertEquals("com.examples", sn0.packageName());
    assertEquals("com.examples", sn1.packageName());
  }

  @Test
  public void fullyQualifiedName() {
    assertEquals("com.examples.MyService", sn0.fullyQualifiedName());
    assertEquals("com.examples.MyService", sn1.fullyQualifiedName());
  }

  @Test
  public void pathOf() {
    assertEquals("/com.examples.MyService/Method0", sn0.pathOf(method0));
    assertEquals("/com.examples.MyService/Method0", sn1.pathOf(method0));
  }

  @Test
  public void equalsTest() {
    ServiceName snt0 = ServiceName.create("com.examples.MyService");
    ServiceName snt1 = ServiceName.create("com.examples", "MyService");
    ServiceName snt2 = ServiceName.create("com.examples.OtherService");
    ServiceName snt3 = ServiceName.create("org.examples", "MyService");

    assertEquals(sn0, sn1);
    assertEquals(sn0, snt0);
    assertEquals(sn0, snt1);
    assertEquals(sn1, snt0);
    assertEquals(sn1, snt1);

    assertNotEquals(sn0, snt2);
    assertNotEquals(sn0, snt3);
    assertNotEquals(sn1, snt2);
    assertNotEquals(sn1, snt3);
  }

  @Test
  public void hashCodeTest() {
    ServiceName snt0 = ServiceName.create("com.examples.MyService");
    ServiceName snt1 = ServiceName.create("com.examples", "MyService");
    ServiceName snt2 = ServiceName.create("com.examples.OtherService");
    ServiceName snt3 = ServiceName.create("org.examples", "MyService");

    assertEquals(sn0.hashCode(), sn1.hashCode());
    assertEquals(sn0.hashCode(), snt0.hashCode());
    assertEquals(sn0.hashCode(), snt1.hashCode());
    assertEquals(sn1.hashCode(), snt0.hashCode());
    assertEquals(sn1.hashCode(), snt1.hashCode());

    assertNotEquals(sn0.hashCode(), snt2.hashCode());
    assertNotEquals(sn0.hashCode(), snt3.hashCode());
    assertNotEquals(sn1.hashCode(), snt2.hashCode());
    assertNotEquals(sn1.hashCode(), snt3.hashCode());
  }
}
