package examples;

import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.docgen.Source;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import io.vertx.grpcio.server.GrpcServerIndex;
import io.vertx.grpcio.server.ReflectionServiceV1Handler;

import java.util.List;

@Source
public class GrpcIoServerExamples {

  public void createServer(Vertx vertx, HttpServerOptions options) {

    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    HttpServer server = vertx.createHttpServer(options);

    server
      .requestHandler(grpcServer)
      .listen();
  }

  public void stubExample(Vertx vertx, HttpServerOptions options) {

    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    GreeterGrpc.GreeterImplBase service = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    // Bind the service bridge in the gRPC server
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(service);
    serverStub.bind(grpcServer);

    // Start the HTTP/2 server
    vertx.createHttpServer(options)
      .requestHandler(grpcServer)
      .listen();
  }

  public void reflectionExample(Vertx vertx, HttpServerOptions options) {

    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    GreeterGrpc.GreeterImplBase service = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    // Bind the service bridge in the gRPC server
    GrpcIoServiceBridge serverStub = GrpcIoServiceBridge.bridge(service);
    serverStub.bind(grpcServer);

    // Create grpc proto index used by reflection service
    GrpcServerIndex index = new GrpcServerIndex(List.of(service.bindService()));

    // Register proto reflection service handler
    grpcServer.callHandler(ServerReflectionGrpc.getServerReflectionInfoMethod(),
      new ReflectionServiceV1Handler(index));

    // Start the HTTP/2 server
    vertx.createHttpServer(options)
      .requestHandler(grpcServer)
      .listen();
  }
}
