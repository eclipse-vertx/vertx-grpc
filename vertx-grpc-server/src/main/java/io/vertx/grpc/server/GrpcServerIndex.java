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
package io.vertx.grpc.server;

import com.google.protobuf.Descriptors;

import java.util.*;
import java.util.function.Function;

// Modified from https://github.com/quarkusio/quarkus/blob/main/extensions/grpc/reflection/src/main/java/io/quarkus/grpc/reflection/service/GrpcServerIndex.java
public class GrpcServerIndex {

  private final Set<String> names;
  private final Map<String, Descriptors.FileDescriptor> descriptorsByName;
  private final Map<String, Descriptors.FileDescriptor> descriptorsBySymbol;
  private final Map<String, Map<Integer, Descriptors.FileDescriptor>> descriptorsByExtensionAndNumber;
  private final Set<String> processedFiles;

  public GrpcServerIndex() {
    this.names = new HashSet<>();
    this.descriptorsByName = new LinkedHashMap<>();
    this.descriptorsBySymbol = new LinkedHashMap<>();
    this.descriptorsByExtensionAndNumber = new LinkedHashMap<>();
    this.processedFiles = new HashSet<>();
  }

  /**
   * Register a single service descriptor
   *
   * @param descriptor The service descriptor to register
   * @throws IllegalStateException if there are conflicts with existing registered services
   */
  public synchronized void registerService(Descriptors.ServiceDescriptor descriptor) {
    String serviceName = descriptor.getFullName();
    if (names.contains(serviceName)) {
      throw new IllegalStateException("Duplicated gRPC service: " + serviceName);
    }
    names.add(serviceName);

    Descriptors.FileDescriptor fd = descriptor.getFile();
    processFileDescriptorRecursively(fd);
  }

  /**
   * Register multiple service descriptors at once
   *
   * @param descriptors List of service descriptors to register
   * @throws IllegalStateException if there are conflicts with existing registered services
   */
  public synchronized void registerServices(List<Descriptors.ServiceDescriptor> descriptors) {
    for (Descriptors.ServiceDescriptor descriptor : descriptors) {
      registerService(descriptor);
    }
  }

  /**
   * Process a file descriptor and its dependencies recursively
   */
  private void processFileDescriptorRecursively(Descriptors.FileDescriptor fd) {
    if (processedFiles.contains(fd.getName())) {
      return;
    }

    // Add to processed files set to avoid reprocessing
    processedFiles.add(fd.getName());

    // Process the file descriptor
    processFileDescriptor(fd);

    // Process dependencies recursively
    for (Descriptors.FileDescriptor dep : fd.getDependencies()) {
      processFileDescriptorRecursively(dep);
    }
  }

  private void processFileDescriptor(Descriptors.FileDescriptor fd) {
    String name = fd.getName();
    if (descriptorsByName.containsKey(name)) {
      // We've already processed this file descriptor
      return;
    }

    descriptorsByName.put(name, fd);

    for (Descriptors.ServiceDescriptor service : fd.getServices()) {
      processService(service, fd);
    }

    for (Descriptors.Descriptor type : fd.getMessageTypes()) {
      processType(type, fd);
    }

    for (Descriptors.FieldDescriptor extension : fd.getExtensions()) {
      processExtension(extension, fd);
    }
  }

  private void processService(Descriptors.ServiceDescriptor service, Descriptors.FileDescriptor fd) {
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

  private void processType(Descriptors.Descriptor type, Descriptors.FileDescriptor fd) {
    String fullyQualifiedTypeName = type.getFullName();
    if (descriptorsBySymbol.containsKey(fullyQualifiedTypeName)) {
      throw new IllegalStateException("Type already defined: " + fullyQualifiedTypeName);
    }
    descriptorsBySymbol.put(fullyQualifiedTypeName, fd);
    for (Descriptors.FieldDescriptor extension : type.getExtensions()) {
      processExtension(extension, fd);
    }
    for (Descriptors.Descriptor nestedType : type.getNestedTypes()) {
      processType(nestedType, fd);
    }
  }

  private void processExtension(Descriptors.FieldDescriptor extension, Descriptors.FileDescriptor fd) {
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

  /**
   * Get the set of registered service names
   */
  public Set<String> getServiceNames() {
    return Collections.unmodifiableSet(names);
  }

  /**
   * Get a file descriptor by its name
   */
  public Descriptors.FileDescriptor getFileDescriptorByName(String name) {
    return descriptorsByName.get(name);
  }

  /**
   * Get a file descriptor by symbol
   */
  public Descriptors.FileDescriptor getFileDescriptorBySymbol(String symbol) {
    return descriptorsBySymbol.get(symbol);
  }

  /**
   * Get a file descriptor by extension and number
   */
  public Descriptors.FileDescriptor getFileDescriptorByExtensionAndNumber(String type, int number) {
    Map<Integer, Descriptors.FileDescriptor> map = descriptorsByExtensionAndNumber
      .getOrDefault(type, Collections.emptyMap());
    return map.get(number);
  }

  /**
   * Get extension numbers of a type
   */
  public Set<Integer> getExtensionNumbersOfType(String type) {
    return descriptorsByExtensionAndNumber.getOrDefault(type, Collections.emptyMap()).keySet();
  }
}
