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

import com.google.protobuf.MessageOrBuilder;
import io.grpc.Compressor;
import io.grpc.Drainable;
import io.grpc.MethodDescriptor;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.ProtobufJsonWriter;
import io.vertx.grpc.common.ProtobufWireFormat;
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
  public boolean accepts(WireFormat format) {
    return format instanceof ProtobufWireFormat;
  }

  @Override
  public GrpcMessage encode(T msg, WireFormat format) throws CodecException {
    Buffer encoded;
    if (format instanceof ProtobufWireFormat) {
      ByteArrayOutputStream output = new ByteArrayOutputStream(); // Improve that ???
      try (InputStream is = marshaller.stream(msg)) {
        OutputStream compressingStream;
        if (compressor == null) {
          compressingStream = output;
        } else {
          compressingStream = compressor.compress(output);
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
        throw new CodecException(e);
      }
      encoded = Buffer.buffer(output.toByteArray());
    } else if (format instanceof JsonWireFormat) {
      JsonWireFormat json = (JsonWireFormat) format;
      if (msg instanceof MessageOrBuilder) {
        encoded = ProtobufJsonWriter.create(json.writerConfig()).write((MessageOrBuilder) msg);
      } else {
        throw new CodecException();
      }
    } else {
      throw new AssertionError();
    }
    return GrpcMessage.message(compressor == null ? "identity" : compressor.getMessageEncoding(), format, encoded);
  }
}
