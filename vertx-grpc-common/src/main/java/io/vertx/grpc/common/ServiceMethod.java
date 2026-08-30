/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.common;

import io.vertx.codegen.annotations.GenIgnore;

/**
 * Bundle all the bits required to call or bind a grpc service method.
 */
@GenIgnore(GenIgnore.PERMITTED_TYPE)
public interface ServiceMethod<I, O> {

  static <Req, Resp> ServiceMethod<Resp, Req> client(ServiceName serviceName, String methodName, GrpcMessageEncoder<Req> encoder, GrpcMessageDecoder<Resp> decoder) {
    return client(serviceName, methodName, null, encoder, decoder);
  }

  static <Req, Resp> ServiceMethod<Resp, Req> client(ServiceName serviceName, String methodName, MethodCardinality cardinality, GrpcMessageEncoder<Req> encoder, GrpcMessageDecoder<Resp> decoder) {
    return new ServiceMethod<>() {
      @Override
      public ServiceName serviceName() {
        return serviceName;
      }
      @Override
      public String methodName() {
        return methodName;
      }
      @Override
      public MethodCardinality cardinality() {
        return cardinality;
      }
      @Override
      public GrpcMessageDecoder<Resp> decoder() {
        return decoder;
      }
      @Override
      public GrpcMessageEncoder<Req> encoder() {
        return encoder;
      }
    };
  }

  static <Req, Resp> ServiceMethod<Req, Resp> server(ServiceName serviceName, String methodName, GrpcMessageEncoder<Resp> encoder, GrpcMessageDecoder<Req> decoder) {
    return server(serviceName, methodName, null, encoder, decoder);
  }

  static <Req, Resp> ServiceMethod<Req, Resp> server(ServiceName serviceName, String methodName, MethodCardinality cardinality, GrpcMessageEncoder<Resp> encoder, GrpcMessageDecoder<Req> decoder) {
    return new ServiceMethod<>() {
      @Override
      public ServiceName serviceName() {
        return serviceName;
      }
      @Override
      public String methodName() {
        return methodName;
      }
      @Override
      public MethodCardinality cardinality() {
        return cardinality;
      }
      @Override
      public Boolean clientStreaming() {
        return cardinality != null ? cardinality == MethodCardinality.CLIENT_STREAMING || cardinality == MethodCardinality.BIDI_STREAMING : null;
      }
      @Override
      public Boolean serverStreaming() {
        return cardinality != null ? cardinality == MethodCardinality.SERVER_STREAMING || cardinality == MethodCardinality.BIDI_STREAMING : null;
      }
      @Override
      public GrpcMessageDecoder<Req> decoder() {
        return decoder;
      }
      @Override
      public GrpcMessageEncoder<Resp> encoder() {
        return encoder;
      }
    };
  }

  /**
   * @return the service name.
   */
  ServiceName serviceName();

  /**
   * @return the method name
   */
  String methodName();

  /**
   * @return the method cardinality, it might be {@code null} when its unknown.
   */
  MethodCardinality cardinality();

  /**
   * @return whether the client side sends a stream of requests, {@code null} when this is not known
   */
  default Boolean clientStreaming() {
    return cardinality() != null ? cardinality() == MethodCardinality.CLIENT_STREAMING || cardinality() == MethodCardinality.BIDI_STREAMING : null;
  }

  /**
   * @return whether the server side sends a stream of responses, {@code null} when this is not known
   */
  default Boolean serverStreaming() {
    return cardinality() != null ? cardinality() == MethodCardinality.SERVER_STREAMING || cardinality() == MethodCardinality.BIDI_STREAMING : null;
  }

  /**
   * Computes the fully qualified method name for a gRPC service method.
   * The name is constructed by combining the fully qualified service name
   * and the method name, separated by a slash ('/').
   *
   * @return the fully qualified method name in the format "fullyQualifiedServiceName/methodName".
   */
  default String fullMethodName() {
    return serviceName().fullyQualifiedName() + "/" + methodName();
  }

  /**
   * @return the message decoder
   */
  GrpcMessageDecoder<I> decoder();

  /**
   * @return the message encoder
   */
  GrpcMessageEncoder<O> encoder();

}
