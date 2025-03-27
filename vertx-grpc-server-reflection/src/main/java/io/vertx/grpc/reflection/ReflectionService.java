package io.vertx.grpc.reflection;

import com.google.protobuf.Descriptors;
import io.grpc.reflection.v1.ServerReflectionProto;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;

public class ReflectionService implements Service {

  private static final ServiceName SERVICE_NAME = ServiceName.create("grpc.reflection.v1.ServerReflection");
  private static final Descriptors.ServiceDescriptor SERVICE_DESCRIPTOR = ServerReflectionProto.getDescriptor().findServiceByName("ServerReflection");

  @Override
  public ServiceName name() {
    return SERVICE_NAME;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return SERVICE_DESCRIPTOR;
  }

  @Override
  public void bind(GrpcServer server) {
    server.callHandler(GrpcServerReflectionHandler.SERVICE_METHOD, new GrpcServerReflectionHandler(server));
  }
}
