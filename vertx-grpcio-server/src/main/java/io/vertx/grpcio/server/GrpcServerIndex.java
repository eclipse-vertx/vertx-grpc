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
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;

import java.util.*;
import java.util.function.Function;

// Copied from https://github.com/quarkusio/quarkus/blob/main/extensions/grpc/reflection/src/main/java/io/quarkus/grpc/reflection/service/GrpcServerIndex.java
public class GrpcServerIndex {

  private final Set<String> names;
  private final Map<String, Descriptors.FileDescriptor> descriptorsByName;
  private final Map<String, Descriptors.FileDescriptor> descriptorsBySymbol;
  private final Map<String, Map<Integer, Descriptors.FileDescriptor>> descriptorsByExtensionAndNumber;

  public GrpcServerIndex(List<ServerServiceDefinition> definitions) {
    Queue<Descriptors.FileDescriptor> fileDescriptorsToProcess = new ArrayDeque<>();
    Set<String> files = new HashSet<>();
    Set<String> names = new HashSet<>();
    Map<String, Descriptors.FileDescriptor> descriptorsByName = new LinkedHashMap<>();
    Map<String, Descriptors.FileDescriptor> descriptorsBySymbol = new LinkedHashMap<>();
    Map<String, Map<Integer, Descriptors.FileDescriptor>> descriptorsByExtensionAndNumber = new LinkedHashMap<>();

    // Collect the services
    for (ServerServiceDefinition definition : definitions) {
      ServiceDescriptor serviceDescriptor = definition.getServiceDescriptor();
      if (serviceDescriptor.getSchemaDescriptor() instanceof ProtoFileDescriptorSupplier) {
        ProtoFileDescriptorSupplier supplier = (ProtoFileDescriptorSupplier) serviceDescriptor
          .getSchemaDescriptor();
        Descriptors.FileDescriptor fd = supplier.getFileDescriptor();
        String serviceName = serviceDescriptor.getName();
        if (names.contains(serviceName)) {
          throw new IllegalStateException("Duplicated gRPC service: " + serviceName);
        }
        names.add(serviceName);

        if (!files.contains(fd.getName())) {
          files.add(fd.getName());
          fileDescriptorsToProcess.add(fd);
        }
      }
    }

    // Traverse the set of service and add dependencies
    while (!fileDescriptorsToProcess.isEmpty()) {
      Descriptors.FileDescriptor fd = fileDescriptorsToProcess.remove();
      processFileDescriptor(fd, descriptorsByName, descriptorsBySymbol, descriptorsByExtensionAndNumber);
      for (Descriptors.FileDescriptor dep : fd.getDependencies()) {
        if (!files.contains(dep.getName())) {
          files.add(dep.getName());
          fileDescriptorsToProcess.add(dep);
        }
      }
    }

    this.descriptorsByName = Collections.unmodifiableMap(descriptorsByName);
    this.descriptorsByExtensionAndNumber = Collections.unmodifiableMap(descriptorsByExtensionAndNumber);
    this.descriptorsBySymbol = Collections.unmodifiableMap(descriptorsBySymbol);
    this.names = Collections.unmodifiableSet(names);
  }

  public Set<String> getServiceNames() {
    return names;
  }

  public Descriptors.FileDescriptor getFileDescriptorByName(String name) {
    return descriptorsByName.get(name);
  }

  public Descriptors.FileDescriptor getFileDescriptorBySymbol(String symbol) {
    return descriptorsBySymbol.get(symbol);
  }

  public Descriptors.FileDescriptor getFileDescriptorByExtensionAndNumber(String type, int number) {
    Map<Integer, Descriptors.FileDescriptor> map = descriptorsByExtensionAndNumber
      .getOrDefault(type, Collections.emptyMap());
    return map.get(number);
  }

  public Set<Integer> getExtensionNumbersOfType(String type) {
    return descriptorsByExtensionAndNumber.getOrDefault(type, Collections.emptyMap()).keySet();
  }

  private void processFileDescriptor(Descriptors.FileDescriptor fd,
                                     Map<String, Descriptors.FileDescriptor> descriptorsByName,
                                     Map<String, Descriptors.FileDescriptor> descriptorsBySymbol,
                                     Map<String, Map<Integer, Descriptors.FileDescriptor>> descriptorsByExtensionAndNumber) {
    String name = fd.getName();
    if (descriptorsByName.containsKey(name)) {
      throw new IllegalStateException("File name already used: " + name);
    }
    descriptorsByName.put(name, fd);
    for (Descriptors.ServiceDescriptor service : fd.getServices()) {
      processService(service, fd, descriptorsBySymbol);
    }
    for (Descriptors.Descriptor type : fd.getMessageTypes()) {
      processType(type, fd, descriptorsBySymbol, descriptorsByExtensionAndNumber);
    }
    for (Descriptors.FieldDescriptor extension : fd.getExtensions()) {
      processExtension(extension, fd, descriptorsByExtensionAndNumber);
    }
  }

  private void processService(Descriptors.ServiceDescriptor service, Descriptors.FileDescriptor fd,
                              Map<String, Descriptors.FileDescriptor> descriptorsBySymbol) {
    String fullyQualifiedServiceName = service.getFullName();
    if (descriptorsBySymbol.containsKey(fullyQualifiedServiceName)) {
      throw new IllegalStateException("Service already defined: " + fullyQualifiedServiceName);
    }
    descriptorsBySymbol.put(fullyQualifiedServiceName, fd);
    for (Descriptors.MethodDescriptor method : service.getMethods()) {
      String fullyQualifiedMethodName = method.getFullName();
      if (descriptorsBySymbol.containsKey(fullyQualifiedMethodName)) {
        throw new IllegalStateException(
          "Method already defined: " + fullyQualifiedMethodName + " in " + fullyQualifiedServiceName);
      }
      descriptorsBySymbol.put(fullyQualifiedMethodName, fd);
    }
  }

  private void processType(Descriptors.Descriptor type, Descriptors.FileDescriptor fd,
                           Map<String, Descriptors.FileDescriptor> descriptorsBySymbol,
                           Map<String, Map<Integer, Descriptors.FileDescriptor>> descriptorsByExtensionAndNumber) {
    String fullyQualifiedTypeName = type.getFullName();
    if (descriptorsBySymbol.containsKey(fullyQualifiedTypeName)) {
      throw new IllegalStateException("Type already defined: " + fullyQualifiedTypeName);
    }
    descriptorsBySymbol.put(fullyQualifiedTypeName, fd);
    for (Descriptors.FieldDescriptor extension : type.getExtensions()) {
      processExtension(extension, fd, descriptorsByExtensionAndNumber);
    }
    for (Descriptors.Descriptor nestedType : type.getNestedTypes()) {
      processType(nestedType, fd, descriptorsBySymbol, descriptorsByExtensionAndNumber);
    }
  }

  private void processExtension(Descriptors.FieldDescriptor extension, Descriptors.FileDescriptor fd,
                                Map<String, Map<Integer, Descriptors.FileDescriptor>> descriptorsByExtensionAndNumber) {
    String extensionName = extension.getContainingType().getFullName();
    int extensionNumber = extension.getNumber();

    descriptorsByExtensionAndNumber.computeIfAbsent(extensionName,
      new Function<String, Map<Integer, Descriptors.FileDescriptor>>() {
        @Override
        public Map<Integer, Descriptors.FileDescriptor> apply(String s) {
          return new HashMap<>();
        }
      });

    if (descriptorsByExtensionAndNumber.get(extensionName).containsKey(extensionNumber)) {
      throw new IllegalStateException(
        "Extension name " + extensionName + " and number " + extensionNumber + " are already defined");
    }
    descriptorsByExtensionAndNumber.get(extensionName).put(extensionNumber, fd);
  }

}
