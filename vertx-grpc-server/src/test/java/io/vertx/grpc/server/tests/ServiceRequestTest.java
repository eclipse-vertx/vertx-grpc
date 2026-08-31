package io.vertx.grpc.server.tests;

import com.google.protobuf.Descriptors;
import io.grpc.*;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.*;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.common.tests.TestServiceGrpc;
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
      public <Req, Resp> Handler<GrpcServerRequest<Req, Resp>> handler(ServiceMethod<Req, Resp> method) {
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
