package io.vertx.grpc.server;

import com.google.protobuf.Descriptors;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Handler;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.impl.ServiceImpl;

import java.util.List;
import java.util.Optional;

/**
 * Provides metadata about a gRPC service.
 * <p>
 * This interface gives access to both the name and the service descriptor, which contains detailed information about the service's methods, input and output types, and other
 * metadata defined in the protobuf service definition.
 */
@GenIgnore(GenIgnore.PERMITTED_TYPE)
public interface Service {

  /**
   * Creates a new Service instance with the specified service name and descriptor.
   *
   * @param serviceName the name of the gRPC service
   * @return a new Service instance
   */
  static Service service(ServiceName serviceName) {
    return new ServiceImpl(serviceName);
  }

  /**
   * Get the name of the service.
   *
   * @return the name of the service
   */
  ServiceName name();

  /**
   * Get the service descriptor that contains detailed information about the service.
   *
   * @return the service descriptor
   */
  Descriptors.ServiceDescriptor descriptor();

  /**
   * Set the {@link Descriptors.ServiceDescriptor} for this service.
   *
   * @param descriptor the service descriptor
   * @return a reference to this, so the API can be used fluently
   */
  Service descriptor(Descriptors.ServiceDescriptor descriptor);

  /**
   * Set a service method call handler that handles any call made to the server for the {@code fullMethodName } name method.
   *
   * @param handler the service method call handler
   * @param serviceMethod the service method
   * @return a reference to this, so the API can be used fluently
   */
  <Req, Resp> Service callHandler(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler);

  /**
   * Binds this service and all its registered method handlers to the specified gRPC server.
   * This allows the server to handle requests for this service.
   *
   * @param server the gRPC server to bind this service to
   * @return a reference to this, so the API can be used fluently
   */
  Service bind(GrpcServer server);

  /**
   * Get a list of all method descriptors for this service.
   *
   * @return list of method descriptors
   */
  default List<Descriptors.MethodDescriptor> methodDescriptors() {
    return descriptor().getMethods();
  }

  /**
   * Get a method descriptor by service.
   *
   * @param methodName the service of the method
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
   * Get the full path for a method, which can be used for making gRPC calls. The format is "/package.ServiceName/MethodName".
   *
   * @param methodName the name of the method
   * @return the full path for the method
   * @throws IllegalArgumentException if the method does not exist
   */
  default String pathOfMethod(String methodName) {
    if (!hasMethod(methodName)) {
      throw new IllegalArgumentException("Method not found: " + methodName);
    }
    return name().pathOf(methodName);
  }
}
