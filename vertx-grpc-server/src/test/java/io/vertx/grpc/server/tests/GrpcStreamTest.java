package io.vertx.grpc.server.tests;

import io.grpc.*;
import io.vertx.core.MultiMap;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.common.tests.TestServiceGrpc;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.impl.GrpcServerImpl;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class GrpcStreamTest extends ServerTestBase {

  @Test
  public void testUnary(TestContext should) {

    GrpcServerImpl server = (GrpcServerImpl) GrpcServer.server(vertx);

    server.streamHandler(UNARY, methodCall -> {
      GrpcStream stream = methodCall.stream();
      AtomicReference<String> name = new AtomicReference<>();
      stream.handler(frame -> {
        if (frame.type() == GrpcFrameType.MESSAGE) {
          Request request = methodCall.messageDecoder().decode(((GrpcMessageFrame) frame).message());
          name.set(request.getName());
        }
      });
      stream.endHandler(v -> {
        String value = "Hello " + name.get();
        Reply reply = Reply.newBuilder().setMessage(value).build();
        GrpcMessage encoded = methodCall.messageEncoder().encode(reply, WireFormat.PROTOBUF);
        stream.write(new DefaultGrpcHeadersFrame(WireFormat.PROTOBUF, "identity", MultiMap.caseInsensitiveMultiMap()));
        stream.write(new DefaultGrpcMessageFrame(encoded));
        stream.end(new DefaultGrpcTrailersFrame(GrpcStatus.OK, null, MultiMap.caseInsensitiveMultiMap()));
      });
      stream.resume();
    });

    startServer(server);

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(ClientInterceptors.intercept(channel));
    Request request = Request.newBuilder().setName("Julien").build();
    Reply res = stub.unary(request);
    should.assertEquals("Hello Julien", res.getMessage());
  }
}
