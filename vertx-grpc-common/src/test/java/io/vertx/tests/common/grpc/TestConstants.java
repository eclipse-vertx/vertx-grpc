/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.common.grpc;

import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceName;

import static io.vertx.grpc.common.GrpcMessageDecoder.decoder;
import static io.vertx.grpc.common.GrpcMessageEncoder.encoder;

public final class TestConstants {

  public static final ServiceName TEST_SERVICE = ServiceName.create("io.vertx.tests.common.grpc.tests.TestService");
  public static final GrpcMessageEncoder<Empty> EMPTY_ENC = encoder();
  public static final GrpcMessageDecoder<Empty> EMPTY_DEC = decoder(Empty.newBuilder());
  public static final GrpcMessageEncoder<Request> REQUEST_ENC = encoder();
  public static final GrpcMessageDecoder<Request> REQUEST_DEC = decoder(Request.newBuilder());
  public static final GrpcMessageEncoder<Reply> REPLY_ENC = encoder();
  public static final GrpcMessageDecoder<Reply> REPLY_DEC = decoder(Reply.newBuilder());

  private TestConstants() {
  }
}
