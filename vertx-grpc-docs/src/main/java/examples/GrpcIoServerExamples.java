package examples;

import examples.grpc.*;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
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

    // Bind the service in the gRPC server
    grpcServer.addService(service);

    // Start the HTTP/2 server
    vertx.createHttpServer(options)
      .requestHandler(grpcServer)
      .listen();
  }

  public void reflectionExample(Vertx vertx, HttpServerOptions options) {
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    // Add reflection service
    grpcServer.addService(ReflectionService.v1());

    GreeterGrpc.GreeterImplBase greeterService = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    // Bind the service in the gRPC server
    grpcServer.addService(greeterService);

    // Start the HTTP/2 server
    vertx.createHttpServer(options)
      .requestHandler(grpcServer)
      .listen();
  }

  public void idiomaticStubExample(Vertx vertx, HttpServerOptions options) {

    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    BindableService service = GreeterGrpcIo.bindableServiceOf(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
      }
    });

    // Bind the service in the gRPC server
    grpcServer.addService(service);

    // Start the HTTP/2 server
    vertx.createHttpServer(options)
      .requestHandler(grpcServer)
      .listen();
  }
}
