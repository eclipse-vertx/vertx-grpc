/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpcio.common.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import io.grpc.InternalMetadata;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.netty.util.AsciiString;
import io.vertx.core.MultiMap;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class Utils {

  public static void writeMetadata(Metadata metadata, MultiMap mmap) {
    byte[][] array = InternalMetadata.serialize(metadata);
    for (int i = 0; i < array.length; i += 2) {
      AsciiString key = new AsciiString(array[i], false);
      AsciiString value;
      if (key.endsWith("-bin")) {
        value = new AsciiString(Base64.getEncoder().encode(array[i + 1]), false);
      } else {
        value = new AsciiString(array[i + 1], false);
      }
      mmap.add(key, value);
    }
  }

  public static Metadata readMetadata(MultiMap headers) {
    List<Map.Entry<String, String>> entries = headers.entries();
    byte[][] array = new byte[entries.size() * 2][];
    int idx = 0;
    for (Map.Entry<String, String> entry : entries) {
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      array[idx++] = key.getBytes(StandardCharsets.UTF_8);
      String value = entry.getValue();
      byte[] data;
      if (key.endsWith("-bin")) {
        data = Base64.getDecoder().decode(value);
      } else {
        data = value.getBytes(StandardCharsets.UTF_8);
      }
      array[idx++] = data;
    }
    return InternalMetadata.newMetadata(array);
  }

  public static <T extends MessageOrBuilder> MethodDescriptor.Marshaller<T> marshallerFor(Supplier<Message.Builder> b) {
    return new MethodDescriptor.PrototypeMarshaller<T>() {
      @Nullable
      @Override
      public T getMessagePrototype() {
        Message.Builder builder = b.get();
        return (T) builder.build();
      }
      @Override
      public Class<T> getMessageClass() {
        throw new UnsupportedOperationException();
      }
      @Override
      public InputStream stream(T value) {
        String res;
        try {
          res = JsonFormat.printer().print(value);
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException(e);
        }
        return new ByteArrayInputStream(res.getBytes(StandardCharsets.UTF_8));
      }

      @Override
      public T parse(InputStream stream) {
        try {
          Message.Builder builder = b.get();
          byte[] bytes = stream.readAllBytes();
          JsonFormat.parser().merge(new String(bytes, StandardCharsets.UTF_8), builder);
          return (T) builder.build();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}
