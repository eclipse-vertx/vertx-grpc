package examples;

import examples.grpc.GreeterGrpc;
import examples.grpc.HelloReply;
import examples.grpc.HelloRequest;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.docgen.Source;
import io.vertx.grpc.reflection.ReflectionService;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;

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

    // Add reflection service
    grpcServer.addService(ReflectionService.v1());

    GreeterGrpc.GreeterImplBase greeterImpl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    // Bind the service in the gRPC server
    GrpcIoServiceBridge greeterService = GrpcIoServiceBridge.bridge(greeterImpl);

    grpcServer.addService(greeterService);

    // Start the HTTP/2 server
    vertx.createHttpServer(options)
      .requestHandler(grpcServer)
      .listen();
  }
}
