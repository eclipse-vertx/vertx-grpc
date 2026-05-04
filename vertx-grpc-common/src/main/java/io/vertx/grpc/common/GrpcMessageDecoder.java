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

import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.Parser;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

import java.util.function.Supplier;

public interface GrpcMessageDecoder<T> {

  /**
   * Create a decoder for a given protobuf {@link Parser}.
   * @param messageOrBuilder the message or builder instance that returns decoded messages of type {@code <T>}
   * @return the message decoder
   */
  static <T> GrpcMessageDecoder<T> decoder(MessageOrBuilder messageOrBuilder) {
    Message dit = messageOrBuilder.getDefaultInstanceForType();
    Parser<T> parser = (Parser<T>) dit.getParserForType();
    return new GrpcMessageDecoder<>() {
      @Override
      public T decode(GrpcMessage msg) throws CodecException {
        WireFormat format = msg.format();
        if (format instanceof ProtobufWireFormat) {
          try {
            return parser.parseFrom(msg.payload().getBytes());
          } catch (InvalidProtocolBufferException e) {
            throw new CodecException(e);
          }
        } else if (format instanceof JsonWireFormat) {
          JsonWireFormat json = (JsonWireFormat) format;
          Message.Builder builder = dit.toBuilder();
          ProtobufJsonReader.create(json).merge(msg.payload(), builder);
          return (T) builder.build();
        } else {
          throw new IllegalArgumentException("Invalid wire format: " + format);
        }
      }
      @Override
      public boolean accepts(WireFormat format) {
        return true;
      }
      @Override
      public Descriptors.Descriptor messageDescriptor() {
        return dit.getDescriptorForType();
      }
    };
  }

  GrpcMessageDecoder<Buffer> IDENTITY = new GrpcMessageDecoder<>() {
    @Override
    public Buffer decode(GrpcMessage msg) throws CodecException {
      return msg.payload();
    }
    @Override
    public boolean accepts(WireFormat format) {
      return true;
    }
  };

  /**
   * Create a decoder for a given protobuf {@link Parser}.
   * @param builder the supplier of a message builder
   * @return the message decoder
   */
  static <T> GrpcMessageDecoder<T> json(Supplier<Message.Builder> builder) {
    return new GrpcMessageDecoder<>() {
      @Override
      public T decode(GrpcMessage msg) throws CodecException {
        JsonWireFormat json = (JsonWireFormat) msg.format();
        Message.Builder builderInstance = builder.get();
        ProtobufJsonReader.create(json).merge(msg.payload(), builderInstance);
        return (T) builderInstance.build();
      }
      @Override
      public boolean accepts(WireFormat format) {
        return format instanceof JsonWireFormat;
      }
    };
  }

  /**
   * Create a decoder in JSON format decoding to instances of the {@code clazz} using
   * {@link Json#decodeValue(Buffer, Class)} (Jackson Databind is required).
   *
   * @param clazz the java type to decode
   * @return a decoder that decodes messages to instance of {@code clazz} in JSON format.
   */
  static <T> GrpcMessageDecoder<T> json(Class<T> clazz) {
    return new GrpcMessageDecoder<>() {
      @Override
      public T decode(GrpcMessage msg) throws CodecException {
        if (!(msg.format() instanceof JsonWireFormat)) {
          throw new CodecException("Was expecting a json message");
        }
        try {
          return Json.decodeValue(msg.payload(), clazz);
        } catch (DecodeException e) {
          throw new CodecException(e);
        }
      }
      @Override
      public boolean accepts(WireFormat format) {
        return format instanceof JsonWireFormat;
      }
    };
  }

  /**
   * A decoder in JSON format decoding to instances of {@link JsonObject}.
   */
  GrpcMessageDecoder<JsonObject> JSON_OBJECT = new GrpcMessageDecoder<JsonObject>() {
    @Override
    public JsonObject decode(GrpcMessage msg) throws CodecException {
      Object val = JSON_VALUE.decode(msg);
      if (val instanceof JsonObject) {
        return (JsonObject) val;
      } else {
        throw new CodecException("Was expecting an instance of JsonObject instead of " + val.getClass().getName());
      }
    }
    @Override
    public boolean accepts(WireFormat format) {
      return format instanceof JsonWireFormat;
    }
  };

  /**
   * A decoder in JSON format decoding arbitrary JSON values: {@link JsonObject}, {@link JsonArray} or string/number/boolean/null
   */
  GrpcMessageDecoder<Object> JSON_VALUE = new GrpcMessageDecoder<Object>() {
    @Override
    public Object decode(GrpcMessage msg) throws CodecException {
      if (!(msg.format() instanceof JsonWireFormat)) {
        throw new CodecException("Was expecting a json message");
      }
      try {
        return Json.decodeValue(msg.payload());
      } catch (DecodeException e) {
        throw new CodecException(e);
      }
    }
    @Override
    public boolean accepts(WireFormat format) {
      return format instanceof JsonWireFormat;
    }
  };

  /**
   * Decodes a given gRPC message into an object of type {@code T}.
   *
   * @param msg the gRPC message to decode
   * @return the decoded message of type {@code T}
   * @throws CodecException if the decoding process fails
   */
  T decode(GrpcMessage msg) throws CodecException;

  /**
   * Determines whether the given {@link WireFormat} is supported by this decoder.
   *
   * @param format the wire format to check
   * @return true if the given wire format is accepted, false otherwise
   */
  boolean accepts(WireFormat format);

  /**
   * Returns the protobuf message descriptor if this decoder was created from a protobuf message type, {@code null} otherwise.
   *
   * @return the protobuf descriptor or {@code null}
   */
  @Unstable
  default Descriptors.Descriptor messageDescriptor() {
    return null;
  }

}
