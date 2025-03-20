package io.vertx.grpc.common;

import com.google.protobuf.Descriptors;
import io.vertx.codegen.annotations.GenIgnore;

import java.util.List;
import java.util.Optional;

/**
 * Provides metadata about a gRPC service.
 * <p>
 * This interface gives access to both the service name and the service descriptor,
 * which contains detailed information about the service's methods, input and output types,
 * and other metadata defined in the protobuf service definition.
 */
@GenIgnore(GenIgnore.PERMITTED_TYPE)
public interface Service {

  /**
   * Creates a new Service instance with the specified service name and descriptor.
   *
   * @param serviceName the name of the gRPC service
   * @param serviceDescriptor the descriptor containing detailed information about the service
   * @return a new Service instance
   */
  static Service metadata(ServiceName serviceName, Descriptors.ServiceDescriptor serviceDescriptor) {
    return new Service() {
      @Override
      public ServiceName service() {
        return serviceName;
      }

      @Override
      public Descriptors.ServiceDescriptor serviceDescriptor() {
        return serviceDescriptor;
      }
    };
  }

  /**
   * Get the name of the service.
   *
   * @return the service name
   */
  ServiceName service();

  /**
   * Get the service descriptor that contains detailed information about the service.
   *
   * @return the service descriptor
   */
  Descriptors.ServiceDescriptor serviceDescriptor();

  /**
   * Get a list of all method descriptors for this service.
   *
   * @return list of method descriptors
   */
  default List<Descriptors.MethodDescriptor> methodDescriptors() {
    return serviceDescriptor().getMethods();
  }

  /**
   * Get a method descriptor by name.
   *
   * @param methodName the name of the method
   * @return an Optional containing the method descriptor if found, or empty if not found
   */
  default Optional<Descriptors.MethodDescriptor> methodDescriptor(String methodName) {
    return methodDescriptors().stream()
      .filter(method -> method.getName().equals(methodName))
      .findFirst();
  }

  /**
   * Check if a method exists in this service.
   *
   * @param methodName the name of the method to check
   * @return true if the method exists, false otherwise
   */
  default boolean hasMethod(String methodName) {
    return methodDescriptor(methodName).isPresent();
  }

  /**
   * Get the full path for a method, which can be used for making gRPC calls.
   * The format is "/package.ServiceName/MethodName".
   *
   * @param methodName the name of the method
   * @return the full path for the method
   * @throws IllegalArgumentException if the method does not exist
   */
  default String pathOfMethod(String methodName) {
    if (!hasMethod(methodName)) {
      throw new IllegalArgumentException("Method not found: " + methodName);
    }
    return service().pathOf(methodName);
  }
}
