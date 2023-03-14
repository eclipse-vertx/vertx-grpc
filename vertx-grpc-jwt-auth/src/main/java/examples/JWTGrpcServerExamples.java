package examples;

import io.grpc.BindableService;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.auth.JWTGrpcServer;

public class JWTGrpcServerExamples {

  public void setupJWTServer(BindableService service, JWTAuth authProvider, Vertx vertx) {
    JWTGrpcServer server = JWTGrpcServer.create(vertx, authProvider);
  }

  public void accessUser(JWTGrpcServer jwtGrpcServer) {
    jwtGrpcServer.callHandler(GreeterGrpc.getSayHelloMethod(), true, request -> {
      request.handler(hello -> {
        GrpcServerResponse<HelloRequest, HelloReply> response = request.response();
        User user = request.user();
        HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + hello.getName() + " from " + user.subject()).build();
        response.end(reply);
      });
    });
  }
}
