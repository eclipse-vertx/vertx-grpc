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
package io.vertx.grpcio.common.impl;

import io.grpc.Decompressor;
import io.grpc.KnownLength;
import io.grpc.MethodDescriptor;
import io.vertx.grpc.common.WireFormat;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;

import java.io.IOException;
import java.io.InputStream;

public class BridgeMessageDecoder<T> implements GrpcMessageDecoder<T> {

  private final MethodDescriptor.Marshaller<T> marshaller;
  private final Decompressor decompressor;

  public BridgeMessageDecoder(MethodDescriptor.Marshaller<T> marshaller, Decompressor decompressor) {
    this.marshaller = marshaller;
    this.decompressor = decompressor;
  }

  private static class KnownLengthStream extends ByteBufInputStream implements KnownLength {
    public KnownLengthStream(Buffer buffer) {
      super(((BufferInternal)buffer).getByteBuf(), buffer.length());
    }

    @Override
    public void close() {
      try {
        super.close();
      } catch (IOException ignore) {
      }
    }
  }

  @Override
  public T decode(GrpcMessage msg) {
    try (KnownLengthStream kls = new KnownLengthStream(msg.payload())) {
      if (msg.encoding().equals("identity")) {
        return marshaller.parse(kls);
      } else {
        try (InputStream in = decompressor.decompress(kls)) {
          return marshaller.parse(in);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public WireFormat format() {
    return WireFormat.PROTOBUF;
  }
}
