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
package io.vertx.grpc.common.tests;

import io.vertx.core.http.HttpVersion;
import io.vertx.grpc.common.GrpcError;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GrpcErrorTest {

  @Test
  public void testHttp3ResetCodes() {
    assertEquals(GrpcError.INTERNAL, GrpcError.mapErrorCode(HttpVersion.HTTP_3, 0x0102));
    assertEquals(GrpcError.RESOURCE_EXHAUSTED, GrpcError.mapErrorCode(HttpVersion.HTTP_3, 0x0107));
    assertEquals(GrpcError.UNAVAILABLE, GrpcError.mapErrorCode(HttpVersion.HTTP_3, 0x010B));
    assertEquals(GrpcError.CANCELLED, GrpcError.mapErrorCode(HttpVersion.HTTP_3, 0x010C));
    assertNull(GrpcError.mapErrorCode(HttpVersion.HTTP_3, 0x0111));
  }

  @Test
  public void testResetCodeForVersion() {
    assertEquals(0x08, GrpcError.CANCELLED.resetCode(HttpVersion.HTTP_2));
    assertEquals(0x010C, GrpcError.CANCELLED.resetCode(HttpVersion.HTTP_3));
  }
}
