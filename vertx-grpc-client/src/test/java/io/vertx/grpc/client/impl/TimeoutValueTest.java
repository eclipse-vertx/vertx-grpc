package io.vertx.grpc.client.impl;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class TimeoutValueTest {

  private static final long MAX = 99_999_999;

  @Test
  public void testValue() {
    assertEquals(MAX + "n", GrpcClientRequestImpl.toTimeoutHeader(MAX, TimeUnit.NANOSECONDS));
    assertEquals((MAX + 1) / 1000 + "u", GrpcClientRequestImpl.toTimeoutHeader(MAX + 1, TimeUnit.NANOSECONDS));
    assertEquals(MAX + "u", GrpcClientRequestImpl.toTimeoutHeader(MAX, TimeUnit.MICROSECONDS));
    assertEquals((MAX + 1) / 1000 + "m", GrpcClientRequestImpl.toTimeoutHeader(MAX + 1, TimeUnit.MICROSECONDS));
    assertEquals(MAX + "m", GrpcClientRequestImpl.toTimeoutHeader(MAX, TimeUnit.MILLISECONDS));
    assertEquals((MAX + 1) / 1000 + "S", GrpcClientRequestImpl.toTimeoutHeader(MAX + 1, TimeUnit.MILLISECONDS));
    assertEquals(MAX + "S", GrpcClientRequestImpl.toTimeoutHeader(MAX, TimeUnit.SECONDS));
    assertEquals((MAX + 1) / 60 + "M", GrpcClientRequestImpl.toTimeoutHeader(MAX + 1, TimeUnit.SECONDS));
    assertEquals(MAX + "M", GrpcClientRequestImpl.toTimeoutHeader(MAX, TimeUnit.MINUTES));
    assertEquals((MAX + 1) / 60 + "H", GrpcClientRequestImpl.toTimeoutHeader(MAX + 1, TimeUnit.MINUTES));
    assertEquals(MAX + "H", GrpcClientRequestImpl.toTimeoutHeader(MAX, TimeUnit.HOURS));
    assertEquals(null, GrpcClientRequestImpl.toTimeoutHeader(MAX + 1, TimeUnit.HOURS));
  }
}
