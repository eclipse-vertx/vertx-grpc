package io.vertx.grpc.reflection;

import com.google.protobuf.Descriptors;
import io.vertx.grpc.reflection.v1.ServerReflectionProto;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;

/**
 * Reflection service implementing <a href="https://grpc.io/docs/guides/reflection/">Reflection</a>.
 */
public class ReflectionService implements Service {

  private static final ServiceName V1_SERVICE_NAME = ServiceName.create("grpc.reflection.v1.ServerReflection");
  private static final Descriptors.ServiceDescriptor V1_SERVICE_DESCRIPTOR = ServerReflectionProto.getDescriptor().findServiceByName("ServerReflection");

  /**
   * V1 singleton instance.
   */
  private static final ReflectionService V1_INSTANCE = new ReflectionService();

  /**
   * @return a v1 reflection service
   */
  public static ReflectionService v1() {
    return V1_INSTANCE;
  }

  private ReflectionService() {
  }

  @Override
  public ServiceName name() {
    return V1_SERVICE_NAME;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return V1_SERVICE_DESCRIPTOR;
  }

  @Override
  public void bind(GrpcServer server) {
    server.callHandler(GrpcServerReflectionV1Handler.SERVICE_METHOD, new GrpcServerReflectionV1Handler(server));
  }
}
