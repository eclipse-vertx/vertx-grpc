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
package io.vertx.grpc.server;

import com.google.protobuf.Descriptors;
import io.grpc.reflection.v1.*;
import io.vertx.core.Handler;
import io.vertx.grpc.common.*;

import java.util.*;
import java.util.stream.Collectors;

public class GrpcServerReflectionHandler implements Handler<GrpcServerRequest<ServerReflectionRequest, ServerReflectionResponse>> {

  public static final ServiceMethod<ServerReflectionRequest, ServerReflectionResponse> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("grpc.reflection.v1.ServerReflection"),
    "ServerReflectionInfo",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(ServerReflectionRequest.parser()));

  private final GrpcServer server;

  public GrpcServerReflectionHandler(GrpcServer server) {
    this.server = server;
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
          response.end(getErrorResponse(serverReflectionRequest, GrpcStatus.UNIMPLEMENTED,
            "not implemented " + serverReflectionRequest.getMessageRequestCase()));
      }
    });
  }

  private ServerReflectionResponse getServiceList(ServerReflectionRequest request) {
    // Get name names directly from server metadata
    List<String> serviceNames = server.getServices().stream()
      .map(metadata -> metadata.name().fullyQualifiedName())
      .collect(Collectors.toList());

    ListServiceResponse response = serviceNames.stream()
      .map(s -> ServiceResponse.newBuilder().setName(s).build())
      .collect(ListServiceResponse::newBuilder, ListServiceResponse.Builder::addService, (b1, b2) -> b1.addAllService(b2.getServiceList()))
      .build();

    return ServerReflectionResponse.newBuilder()
      .setValidHost(request.getHost())
      .setOriginalRequest(request)
      .setListServicesResponse(response)
      .build();
  }

  private ServerReflectionResponse getFileByName(ServerReflectionRequest request) {
    String name = request.getFileByFilename();

    // Find file descriptor by name on the fly
    Descriptors.FileDescriptor fd = null;
    for (Service metadata : server.getServices()) {
      if (metadata.descriptor() != null) {
        Descriptors.FileDescriptor serviceFile = metadata.descriptor().getFile();
        if (serviceFile.getName().equals(name)) {
          fd = serviceFile;
          break;
        }

        // Check dependencies if not found in main file
        fd = findFileDescriptorInDependencies(serviceFile, name);
        if (fd != null) {
          break;
        }
      }
    }

    if (fd != null) {
      return getServerReflectionResponse(request, fd);
    } else {
      return getErrorResponse(request, GrpcStatus.NOT_FOUND, "File not found (" + name + ")");
    }
  }

  private Descriptors.FileDescriptor findFileDescriptorInDependencies(Descriptors.FileDescriptor fd, String name) {
    // Check if this file matches
    if (fd.getName().equals(name)) {
      return fd;
    }

    // Check dependencies recursively
    for (Descriptors.FileDescriptor dep : fd.getDependencies()) {
      Descriptors.FileDescriptor result = findFileDescriptorInDependencies(dep, name);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private ServerReflectionResponse getFileContainingSymbol(ServerReflectionRequest request) {
    String symbol = request.getFileContainingSymbol();

    // Find file descriptor containing the symbol on the fly
    Descriptors.FileDescriptor fd = null;
    for (Service metadata : server.getServices()) {
      if (metadata.descriptor() != null) {
        fd = findFileDescriptorBySymbol(metadata.descriptor().getFile(), symbol);
        if (fd != null) {
          break;
        }
      }
    }

    if (fd != null) {
      return getServerReflectionResponse(request, fd);
    } else {
      return getErrorResponse(request, GrpcStatus.NOT_FOUND, "Symbol not found (" + symbol + ")");
    }
  }

  private Descriptors.FileDescriptor findFileDescriptorBySymbol(Descriptors.FileDescriptor fd, String symbol) {
    // Check name symbols in this file
    for (Descriptors.ServiceDescriptor service : fd.getServices()) {
      if (service.getFullName().equals(symbol)) {
        return fd;
      }

      // Check methods
      for (Descriptors.MethodDescriptor method : service.getMethods()) {
        if (method.getFullName().equals(symbol)) {
          return fd;
        }
      }
    }

    // Check message types in this file
    for (Descriptors.Descriptor type : fd.getMessageTypes()) {
      Descriptors.FileDescriptor result = findFileDescriptorByType(type, symbol, fd);
      if (result != null) {
        return result;
      }
    }

    // Check dependencies
    for (Descriptors.FileDescriptor dep : fd.getDependencies()) {
      Descriptors.FileDescriptor result = findFileDescriptorBySymbol(dep, symbol);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private Descriptors.FileDescriptor findFileDescriptorByType(Descriptors.Descriptor type, String symbol, Descriptors.FileDescriptor fd) {
    // Check if this type matches
    if (type.getFullName().equals(symbol)) {
      return fd;
    }

    // Check nested types
    for (Descriptors.Descriptor nestedType : type.getNestedTypes()) {
      Descriptors.FileDescriptor result = findFileDescriptorByType(nestedType, symbol, fd);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private ServerReflectionResponse getFileByExtension(ServerReflectionRequest request) {
    ExtensionRequest extensionRequest = request.getFileContainingExtension();
    String type = extensionRequest.getContainingType();
    int extension = extensionRequest.getExtensionNumber();

    // Find file descriptor containing extension on the fly
    Descriptors.FileDescriptor fd = null;
    for (Service metadata : server.getServices()) {
      if (metadata.descriptor() != null) {
        fd = findFileDescriptorByExtension(metadata.descriptor().getFile(), type, extension);
        if (fd != null) {
          break;
        }
      }
    }

    if (fd != null) {
      return getServerReflectionResponse(request, fd);
    } else {
      return getErrorResponse(request, GrpcStatus.NOT_FOUND,
        "Extension not found (" + type + ", " + extension + ")");
    }
  }

  private Descriptors.FileDescriptor findFileDescriptorByExtension(Descriptors.FileDescriptor fd, String type, int number) {
    // Check extensions in this file
    for (Descriptors.FieldDescriptor extension : fd.getExtensions()) {
      if (extension.getContainingType().getFullName().equals(type) && extension.getNumber() == number) {
        return fd;
      }
    }

    // Check extensions in message types
    for (Descriptors.Descriptor messageType : fd.getMessageTypes()) {
      Descriptors.FileDescriptor result = findFileDescriptorByExtensionInType(messageType, type, number, fd);
      if (result != null) {
        return result;
      }
    }

    // Check dependencies
    for (Descriptors.FileDescriptor dep : fd.getDependencies()) {
      Descriptors.FileDescriptor result = findFileDescriptorByExtension(dep, type, number);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private Descriptors.FileDescriptor findFileDescriptorByExtensionInType(Descriptors.Descriptor type, String typeName, int number, Descriptors.FileDescriptor fd) {
    // Check extensions in this type
    for (Descriptors.FieldDescriptor extension : type.getExtensions()) {
      if (extension.getContainingType().getFullName().equals(typeName) && extension.getNumber() == number) {
        return fd;
      }
    }

    // Check nested types
    for (Descriptors.Descriptor nestedType : type.getNestedTypes()) {
      Descriptors.FileDescriptor result = findFileDescriptorByExtensionInType(nestedType, typeName, number, fd);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private ServerReflectionResponse getAllExtensions(ServerReflectionRequest request) {
    String type = request.getAllExtensionNumbersOfType();
    Set<Integer> extensions = new HashSet<>();

    // Find all extensions on the fly
    for (Service metadata : server.getServices()) {
      if (metadata.descriptor() != null) {
        collectExtensionNumbers(metadata.descriptor().getFile(), type, extensions);
      }
    }

    if (!extensions.isEmpty()) {
      ExtensionNumberResponse.Builder builder = ExtensionNumberResponse.newBuilder()
        .setBaseTypeName(type)
        .addAllExtensionNumber(extensions);
      return ServerReflectionResponse.newBuilder()
        .setValidHost(request.getHost())
        .setOriginalRequest(request)
        .setAllExtensionNumbersResponse(builder)
        .build();
    } else {
      return getErrorResponse(request, GrpcStatus.NOT_FOUND, "Type not found.");
    }
  }

  private void collectExtensionNumbers(Descriptors.FileDescriptor fd, String type, Set<Integer> extensions) {
    // Collect extensions from this file
    for (Descriptors.FieldDescriptor extension : fd.getExtensions()) {
      if (extension.getContainingType().getFullName().equals(type)) {
        extensions.add(extension.getNumber());
      }
    }

    // Collect extensions from message types
    for (Descriptors.Descriptor messageType : fd.getMessageTypes()) {
      collectExtensionNumbersFromType(messageType, type, extensions);
    }

    // Collect from dependencies
    for (Descriptors.FileDescriptor dep : fd.getDependencies()) {
      collectExtensionNumbers(dep, type, extensions);
    }
  }

  private void collectExtensionNumbersFromType(Descriptors.Descriptor type, String typeName, Set<Integer> extensions) {
    // Collect extensions from this type
    for (Descriptors.FieldDescriptor extension : type.getExtensions()) {
      if (extension.getContainingType().getFullName().equals(typeName)) {
        extensions.add(extension.getNumber());
      }
    }

    // Collect from nested types
    for (Descriptors.Descriptor nestedType : type.getNestedTypes()) {
      collectExtensionNumbersFromType(nestedType, typeName, extensions);
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
    ServerReflectionRequest request, GrpcStatus code, String message) {
    return ServerReflectionResponse.newBuilder()
      .setValidHost(request.getHost())
      .setOriginalRequest(request)
      .setErrorResponse(
        ErrorResponse.newBuilder()
          .setErrorCode(code.code)
          .setErrorMessage(message))
      .build();
  }
}
