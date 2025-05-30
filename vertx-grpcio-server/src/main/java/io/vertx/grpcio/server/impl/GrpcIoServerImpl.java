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
package io.vertx.grpcio.server.impl;

import io.grpc.MethodDescriptor;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.*;
import io.vertx.grpc.server.*;
import io.vertx.grpc.server.impl.GrpcServerImpl;
import io.vertx.grpcio.common.impl.BridgeMessageDecoder;
import io.vertx.grpcio.common.impl.BridgeMessageEncoder;
import io.vertx.grpcio.common.impl.Utils;
import io.vertx.grpcio.server.GrpcIoServer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcIoServerImpl extends GrpcServerImpl implements GrpcIoServer {

  public GrpcIoServerImpl(Vertx vertx, GrpcServerOptions options) {
    super(vertx, options);
  }

  @Override
  public GrpcIoServerImpl callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    return (GrpcIoServerImpl) super.callHandler(handler);
  }

  public <Req, Resp> GrpcIoServerImpl callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler) {
    ServiceMethod<Req, Resp> serviceMethod = ServiceMethod.server(ServiceName.create(
      methodDesc.getServiceName()),
      methodDesc.getBareMethodName(),
      new BridgeMessageEncoder<>(methodDesc.getResponseMarshaller(), null),
      new BridgeMessageDecoder<>(methodDesc.getRequestMarshaller(), null)
    );
    return (GrpcIoServerImpl) callHandler(serviceMethod, handler);
  }
}
