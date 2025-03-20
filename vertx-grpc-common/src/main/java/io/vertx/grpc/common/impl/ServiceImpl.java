package io.vertx.grpc.common.impl;

import com.google.protobuf.Descriptors;
import io.vertx.grpc.common.Service;
import io.vertx.grpc.common.ServiceName;

public class ServiceImpl implements Service {

  @Override
  public ServiceName service() {
    return null;
  }

  @Override
  public Descriptors.ServiceDescriptor serviceDescriptor() {
    return null;
  }
}
