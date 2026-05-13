package io.vertx.tests.server;

import com.google.protobuf.Descriptors;
import io.grpc.*;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.*;
import io.vertx.tests.common.grpc.Reply;
import io.vertx.tests.common.grpc.Request;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Test;

import java.util.List;

public class ServiceRequestTest extends ServerTestBase {

  @Test
  public void testUnary(TestContext should) {

    Service service = new Service() {
      @Override
      public ServiceName name() {
        return UNARY.serviceName();
      }
      @Override
      public Descriptors.ServiceDescriptor descriptor() {
        throw new UnsupportedOperationException();
      }
      @Override
      public List<ServiceMethod<?, ?>> methods() {
        return List.of(UNARY);
      }
      @Override
      public <Req, Resp> ServiceMethodInvoker<Req, Resp> invoker(ServiceMethod<Req, Resp> method) {
        return request -> {
          handleUnary((GrpcServerRequest)request);;
        };
      }
      private void handleUnary(GrpcServerRequest<Request, Reply> request) {
        GrpcServerResponse<Request, Reply> response = request.response();
        request.handler(helloRequest -> {
          Reply helloReply = Reply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
          response
            .end(helloReply);
        });
      }
    };

    startServer(GrpcServer.server(vertx).addService(service));

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    Request request = Request.newBuilder().setName("Julien").build();
    Reply res = stub.unary(request);
    should.assertEquals("Hello Julien", res.getMessage());
  }
}
