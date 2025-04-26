package examples;

import examples.grpc.GreeterGrpcService;
import examples.grpc.HelloReply;
import examples.grpc.HelloRequest;
import io.vertx.core.Future;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;

public class GrpcTranscodingExamples {

  public void transcodingRequestResponse(GrpcServer server) {
    Service service = GreeterGrpcService.of(new GreeterGrpcService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
      }
    });

    server.addService(service);
  }
}
