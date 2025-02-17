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
package io.vertx.grpcio.server;

import com.google.protobuf.Descriptors;
import io.grpc.Status;
import io.grpc.reflection.v1.*;
import io.vertx.core.Handler;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Copied from https://github.com/quarkusio/quarkus/blob/main/extensions/grpc/reflection/src/main/java/io/quarkus/grpc/reflection/service/ReflectionServiceV1.java
// And adapted for Vertx
public class ReflectionServiceV1Handler implements Handler<GrpcServerRequest<ServerReflectionRequest, ServerReflectionResponse>> {

  private final GrpcServerIndex index;

  public ReflectionServiceV1Handler(GrpcServerIndex index) {
    this.index = index;
  }

  @Override
  public void handle(GrpcServerRequest<ServerReflectionRequest, ServerReflectionResponse> request) {
    request.handler(serverReflectionRequest -> {
      GrpcServerResponse<ServerReflectionRequest, ServerReflectionResponse> response = request.response();
      switch (serverReflectionRequest.getMessageRequestCase()) {
        case LIST_SERVICES:
          response.end(getServiceList(serverReflectionRequest));
          break;
        case FILE_BY_FILENAME:
          response.end(getFileByName(serverReflectionRequest));
          break;
        case FILE_CONTAINING_SYMBOL:
          response.end(getFileContainingSymbol(serverReflectionRequest));
          break;
        case FILE_CONTAINING_EXTENSION:
          response.end(getFileByExtension(serverReflectionRequest));
          break;
        case ALL_EXTENSION_NUMBERS_OF_TYPE:
          response.end(getAllExtensions(serverReflectionRequest));
          break;
        default:
          response.end(getErrorResponse(serverReflectionRequest, Status.Code.UNIMPLEMENTED,
            "not implemented " + serverReflectionRequest.getMessageRequestCase()));
      }
    });
  }

  private ServerReflectionResponse getServiceList(ServerReflectionRequest request) {
    ListServiceResponse response = index.getServiceNames().stream()
      .map(new Function<String, ServiceResponse>() { // NOSONAR
        @Override
        public ServiceResponse apply(String s) {
          return ServiceResponse.newBuilder().setName(s).build();
        }
      })
      .collect(new Supplier<ListServiceResponse.Builder>() {
                 @Override
                 public ListServiceResponse.Builder get() {
                   return ListServiceResponse.newBuilder();
                 }
               },
        new BiConsumer<ListServiceResponse.Builder, ServiceResponse>() {
          @Override
          public void accept(ListServiceResponse.Builder builder, ServiceResponse value) {
            builder.addService(value);
          }
        },
        new BiConsumer<ListServiceResponse.Builder, ListServiceResponse.Builder>() { // NOSONAR
          @Override
          public void accept(ListServiceResponse.Builder b1,
                             ListServiceResponse.Builder b2) {
            b1.addAllService(b2.getServiceList());
          }
        })
      .build();

    return ServerReflectionResponse.newBuilder()
      .setValidHost(request.getHost())
      .setOriginalRequest(request)
      .setListServicesResponse(response)
      .build();
  }

  private ServerReflectionResponse getFileByName(ServerReflectionRequest request) {
    String name = request.getFileByFilename();
    Descriptors.FileDescriptor fd = index.getFileDescriptorByName(name);
    if (fd != null) {
      return getServerReflectionResponse(request, fd);
    } else {
      return getErrorResponse(request, Status.Code.NOT_FOUND, "File not found (" + name + ")");
    }
  }

  private ServerReflectionResponse getFileContainingSymbol(ServerReflectionRequest request) {
    String symbol = request.getFileContainingSymbol();
    Descriptors.FileDescriptor fd = index.getFileDescriptorBySymbol(symbol);
    if (fd != null) {
      return getServerReflectionResponse(request, fd);
    } else {
      return getErrorResponse(request, Status.Code.NOT_FOUND, "Symbol not found (" + symbol + ")");
    }
  }

  private ServerReflectionResponse getFileByExtension(ServerReflectionRequest request) {
    ExtensionRequest extensionRequest = request.getFileContainingExtension();
    String type = extensionRequest.getContainingType();
    int extension = extensionRequest.getExtensionNumber();
    Descriptors.FileDescriptor fd = index.getFileDescriptorByExtensionAndNumber(type, extension);
    if (fd != null) {
      return getServerReflectionResponse(request, fd);
    } else {
      return getErrorResponse(request, Status.Code.NOT_FOUND,
        "Extension not found (" + type + ", " + extension + ")");
    }
  }

  private ServerReflectionResponse getAllExtensions(ServerReflectionRequest request) {
    String type = request.getAllExtensionNumbersOfType();
    Set<Integer> extensions = index.getExtensionNumbersOfType(type);
    if (extensions != null) {
      ExtensionNumberResponse.Builder builder = ExtensionNumberResponse.newBuilder()
        .setBaseTypeName(type)
        .addAllExtensionNumber(extensions);
      return ServerReflectionResponse.newBuilder()
        .setValidHost(request.getHost())
        .setOriginalRequest(request)
        .setAllExtensionNumbersResponse(builder)
        .build();
    } else {
      return getErrorResponse(request, Status.Code.NOT_FOUND, "Type not found.");
    }
  }

  private ServerReflectionResponse getServerReflectionResponse(
    ServerReflectionRequest request, Descriptors.FileDescriptor fd) {
    FileDescriptorResponse.Builder fdRBuilder = FileDescriptorResponse.newBuilder();

    // Traverse the descriptors to get the full list of dependencies and add them to the builder
    Set<String> seenFiles = new HashSet<>();
    Queue<Descriptors.FileDescriptor> frontier = new ArrayDeque<>();
    seenFiles.add(fd.getName());
    frontier.add(fd);
    while (!frontier.isEmpty()) {
      Descriptors.FileDescriptor nextFd = frontier.remove();
      fdRBuilder.addFileDescriptorProto(nextFd.toProto().toByteString());
      for (Descriptors.FileDescriptor dependencyFd : nextFd.getDependencies()) {
        if (!seenFiles.contains(dependencyFd.getName())) {
          seenFiles.add(dependencyFd.getName());
          frontier.add(dependencyFd);
        }
      }
    }
    return ServerReflectionResponse.newBuilder()
      .setValidHost(request.getHost())
      .setOriginalRequest(request)
      .setFileDescriptorResponse(fdRBuilder)
      .build();
  }

  private ServerReflectionResponse getErrorResponse(
    ServerReflectionRequest request, Status.Code code, String message) {
    return ServerReflectionResponse.newBuilder()
      .setValidHost(request.getHost())
      .setOriginalRequest(request)
      .setErrorResponse(
        ErrorResponse.newBuilder()
          .setErrorCode(code.value())
          .setErrorMessage(message))
      .build();
  }
}
