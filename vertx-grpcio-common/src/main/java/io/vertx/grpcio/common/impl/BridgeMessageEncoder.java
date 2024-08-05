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

import io.grpc.Compressor;
import io.grpc.Drainable;
import io.grpc.MethodDescriptor;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BridgeMessageEncoder<T> implements GrpcMessageEncoder<T> {

  private MethodDescriptor.Marshaller<T> marshaller;
  private Compressor compressor;

  public BridgeMessageEncoder(MethodDescriptor.Marshaller<T> marshaller, Compressor compressor) {
    this.marshaller = marshaller;
    this.compressor = compressor;
  }

  @Override
  public WireFormat format() {
    return WireFormat.PROTOBUF;
  }

  @Override
  public GrpcMessage encode(T msg) {
    return new GrpcMessage() {
      private Buffer encoded;
      @Override
      public String encoding() {
        return compressor == null ? "identity" : compressor.getMessageEncoding();
      }
      @Override
      public WireFormat format() {
        return WireFormat.PROTOBUF;
      }
      @Override
      public Buffer payload() {
        if (encoded == null) {
          ByteArrayOutputStream baos = new ByteArrayOutputStream(); // Improve that ???
          try (InputStream is = marshaller.stream(msg)) {
            OutputStream compressingStream;
            if (compressor == null) {
              compressingStream = baos;
            } else {
              compressingStream = compressor.compress(baos);
            }
            try (OutputStream o = compressingStream) {
              if (is instanceof Drainable) {
                Drainable stream = (Drainable) is;
                stream.drainTo(compressingStream);
              } else {
                byte[] tmp = new byte[1024];
                int len;
                while ((len = is.read(tmp)) != -1) {
                  o.write(tmp, 0, len);
                }
              }
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          byte[] bytes = baos.toByteArray();
          encoded = Buffer.buffer(bytes);
        }
        return encoded;
      }
    };
  }
}
