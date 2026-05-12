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

import com.google.protobuf.Descriptors;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.impl.GrpcServerImpl;
import io.vertx.grpcio.common.impl.BridgeMessageDecoder;
import io.vertx.grpcio.common.impl.BridgeMessageEncoder;
import io.vertx.grpcio.server.GrpcIoServer;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcIoServerImpl extends GrpcServerImpl implements GrpcIoServer {

  private final List<MethodDescriptor<?, ?>> callHandlerMethods = new CopyOnWriteArrayList<>();

  public GrpcIoServerImpl(Vertx vertx, GrpcServerOptions options) {
    super(vertx, options);
  }

  @Override
  public GrpcIoServerImpl callHandler(Handler<GrpcServerRequest<Buffer, Buffer>> handler) {
    return (GrpcIoServerImpl) super.callHandler(handler);
  }

  public <Req, Resp> GrpcIoServerImpl callHandler(MethodDescriptor<Req, Resp> methodDesc, Handler<GrpcServerRequest<Req, Resp>> handler) {
    ServiceMethod<Req, Resp> serviceMethod = ServiceMethod.server(
      ServiceName.create(methodDesc.getServiceName()),
      methodDesc.getBareMethodName(),
      new BridgeMessageEncoder<>(methodDesc.getResponseMarshaller(), null),
      new BridgeMessageDecoder<>(methodDesc.getRequestMarshaller(), null)
    );

    String serviceName = methodDesc.getServiceName();
    String bareMethodName = methodDesc.getBareMethodName();
    callHandler(serviceMethod, handler);
    callHandlerMethods.removeIf(m -> Objects.equals(serviceName, m.getServiceName()) && Objects.equals(bareMethodName, m.getBareMethodName()));
    if (handler != null) {
      callHandlerMethods.add(methodDesc);
    }

    return this;
  }

  @Override
  public List<Service> services() {
    // Merges services from addService() with those discovered via callHandler(),
    // skipping callHandler entries that duplicate an addService entry.
    // Fix for https://github.com/eclipse-vertx/vertx-grpc/issues/208
    List<Service> services = super.services();

    if (callHandlerMethods.isEmpty()) {
      return services;
    }

    // Group registered method descriptors by their service name, preserving registration order.
    Map<String, List<MethodDescriptor<?, ?>>> methodsByService = new LinkedHashMap<>();
    for (MethodDescriptor<?, ?> methodDesc : callHandlerMethods) {
      methodsByService.computeIfAbsent(methodDesc.getServiceName(), k -> new ArrayList<>()).add(methodDesc);
    }

    Set<String> names = services.stream()
      .map(s -> s.name().fullyQualifiedName())
      .collect(Collectors.toSet());

    List<Service> virtual = new ArrayList<>(services);

    for (Map.Entry<String, List<MethodDescriptor<?, ?>>> entry : methodsByService.entrySet()) {
      String serviceName = entry.getKey();
      if (names.contains(serviceName)) {
        continue;
      }

      Service service = buildCallHandlerService(serviceName, entry.getValue());
      if (service != null) {
        virtual.add(service);
      }
    }

    return virtual;
  }

  private static Service buildCallHandlerService(String serviceName, List<MethodDescriptor<?, ?>> methods) {
    Descriptors.ServiceDescriptor protoService = null;
    for (MethodDescriptor<?, ?> methodDesc : methods) {
      Object schemaDescriptor = methodDesc.getSchemaDescriptor();
      if (schemaDescriptor instanceof ProtoServiceDescriptorSupplier) {
        Descriptors.ServiceDescriptor candidate = ((ProtoServiceDescriptorSupplier) schemaDescriptor).getServiceDescriptor();
        if (candidate != null) {
          protoService = candidate;
          break;
        }
      }
    }
    if (protoService == null) {
      return null;
    }

    List<Descriptors.MethodDescriptor> registered = new ArrayList<>(methods.size());
    for (MethodDescriptor<?, ?> methodDesc : methods) {
      Descriptors.MethodDescriptor m = protoService.findMethodByName(methodDesc.getBareMethodName());
      if (m != null) {
        registered.add(m);
      }
    }

    ServiceName name = ServiceName.create(serviceName);
    Descriptors.ServiceDescriptor descriptor = protoService;
    List<Descriptors.MethodDescriptor> methodDescriptors = Collections.unmodifiableList(registered);

    return new Service() {
      @Override
      public ServiceName name() {
        return name;
      }

      @Override
      public Descriptors.ServiceDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<Descriptors.MethodDescriptor> methodDescriptors() {
        return methodDescriptors;
      }

    };
  }
}
