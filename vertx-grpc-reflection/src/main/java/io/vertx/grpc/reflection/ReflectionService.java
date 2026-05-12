package io.vertx.grpc.reflection;

import com.google.protobuf.Descriptors;
import io.vertx.core.Handler;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.reflection.v1.ServerReflectionProto;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.ServiceContainer;
import io.vertx.grpc.server.impl.ServerAware;

import java.util.List;

/**
 * Reflection service implementing <a href="https://grpc.io/docs/guides/reflection/">Reflection</a>.
 */
public class ReflectionService implements Service, ServerAware {

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
  public List<ServiceMethod<?, ?>> methods() {
    return List.of(GrpcServerReflectionV1Handler.SERVICE_METHOD);
  }

  @Override
  public <Req, Resp> void handle(GrpcServerRequest<Req, Resp> request) {
    if (request.methodName().equals(GrpcServerReflectionV1Handler.SERVICE_METHOD.methodName())) {
      Handler handler = new GrpcServerReflectionV1Handler(server);
      handler.handle(request);
    } else {
      Service.super.handle(request);
    }
  }

  private ServiceContainer server;

  @Override
  public void setServer(ServiceContainer server) {
    this.server = server;
  }

}
