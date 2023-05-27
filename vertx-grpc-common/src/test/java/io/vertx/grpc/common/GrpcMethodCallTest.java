package io.vertx.grpc.common;

import io.vertx.grpc.common.impl.GrpcMethodCall;
import junit.framework.TestCase;

public class GrpcMethodCallTest extends TestCase {

  private GrpcMethodCall grpcMethodCall0;
  private GrpcMethodCall grpcMethodCall1;
  private GrpcMethodCall grpcMethodCall2;


  public void setUp() throws Exception {
    super.setUp();
    grpcMethodCall0 = new GrpcMethodCall("/com.examples.MyService/Method1");
    grpcMethodCall1 = new GrpcMethodCall("/com.examples/MyService/Method2");
    grpcMethodCall2 = new GrpcMethodCall("/MyService/Method3");
  }

  public void testFullMethodName() {
    assertEquals("com.examples.MyService/Method1", grpcMethodCall0.fullMethodName());
    assertEquals("com.examples/MyService/Method2", grpcMethodCall1.fullMethodName());
    assertEquals("MyService/Method3", grpcMethodCall2.fullMethodName());
  }

  public void testServiceName() {
    assertEquals("com.examples.MyService", grpcMethodCall0.serviceName().fullyQualifiedName());
    assertEquals("com.examples/MyService", grpcMethodCall1.serviceName().fullyQualifiedName());
    assertEquals("MyService", grpcMethodCall2.serviceName().fullyQualifiedName());
  }

  public void testMethodName() {
    assertEquals("/Method1", grpcMethodCall0.methodName());
    assertEquals("/Method2", grpcMethodCall1.methodName());
    assertEquals("/Method3", grpcMethodCall2.methodName());
  }
}
