package examples;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.docgen.Source;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.*;

@Source
public class GrpcServerExamples {

  public void createServer(Vertx vertx, HttpServerOptions options) {

    GrpcServer grpcServer = GrpcServer.server(vertx);

    HttpServer server = vertx.createHttpServer(options);

    server
      .requestHandler(grpcServer)
      .listen();
  }

  public void requestResponse(GrpcServer server) {

    ServiceMethod<HelloRequest, HelloReply> serviceMethod = VertxGreeterGrpcServer.SayHello;

    server.callHandler(serviceMethod, request -> {

      request.handler(hello -> {

        GrpcServerResponse<HelloRequest, HelloReply> response = request.response();

        HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + hello.getName()).build();

        response.end(reply);
      });
    });
  }

  public void streamingRequest(GrpcServer server) {

    server.callHandler(VertxStreamingGrpcServer.Sink, request -> {
      request.handler(item -> {
        // Process item
      });
      request.endHandler(v ->{
        // No more items
        // Send the response
        request.response().end(Empty.getDefaultInstance());
      });
      request.exceptionHandler(err -> {
        // Something wrong happened
      });
    });
  }

  public void streamingResponse(GrpcServer server) {

    server.callHandler(VertxStreamingGrpcServer.Source, request -> {
      GrpcServerResponse<Empty, Item> response = request.response();
      request.handler(empty -> {
        for (int i = 0;i < 10;i++) {
          response.write(Item.newBuilder().setValue("1").build());
        }
        response.end();
      });
    });
  }

  public void bidi(GrpcServer server) {

    server.callHandler(VertxStreamingGrpcServer.Pipe, request -> {

      request.handler(item -> request.response().write(item));
      request.endHandler(v -> request.response().end());
    });
  }

  public void requestFlowControl(Vertx vertx, GrpcServerRequest<Item, Empty> request, Item item) {
    // Pause the response
    request.pause();

    performAsyncOperation().onComplete(ar -> {
      // And then resume
      request.resume();
    });
  }

  private Future<Buffer> performAsyncOperation() {
    return Future.succeededFuture();
  }

  private Future<Buffer> performAsyncOperation(Object o) {
    return Future.succeededFuture();
  }

  private Future<GrpcMessage> handleGrpcMessage(GrpcMessage o) {
    return Future.succeededFuture();
  }

  public void responseFlowControl(GrpcServerResponse<Empty, Item> response, Item item) {
    if (response.writeQueueFull()) {
      response.drainHandler(v -> {
        // Writable again
      });
    } else {
      response.write(item);
    }
  }

  public void responseCompression(GrpcServerResponse<Empty, Item> response) {
    if (response.acceptedEncodings().contains("gzip")) {
      response.encoding("gzip");
    }

    // Write items after encoding has been defined
    response.write(Item.newBuilder().setValue("item-1").build());
    response.write(Item.newBuilder().setValue("item-2").build());
    response.write(Item.newBuilder().setValue("item-3").build());
  }

  public void protobufLevelAPI(GrpcServer server) {

    ServiceName greeterServiceName = ServiceName.create("helloworld", "Greeter");

    server.callHandler(request -> {

      if (request.serviceName().equals(greeterServiceName) && request.methodName().equals("SayHello")) {

        request.handler(protoHello -> {
          // Handle protobuf encoded hello
          performAsyncOperation(protoHello)
            .onSuccess(protoReply -> {
              // Reply with protobuf encoded reply
              request.response().end(protoReply);
            }).onFailure(err -> {
              request.response()
                .status(GrpcStatus.ABORTED)
                .end();
            });
        });
      } else {
        request.response()
          .status(GrpcStatus.NOT_FOUND)
          .end();
      }
    });
  }

  public void messageLevelAPI(GrpcServer server) {

    ServiceName greeterServiceName = ServiceName.create("helloworld", "Greeter");

    server.callHandler(request -> {

      if (request.serviceName().equals(greeterServiceName) && request.methodName().equals("SayHello")) {

        request.messageHandler(helloMessage -> {

          // Can be identity or gzip
          String helloEncoding = helloMessage.encoding();

          // Handle hello message
          handleGrpcMessage(helloMessage)
            .onSuccess(replyMessage -> {
              // Reply with reply message

              // Can be identity or gzip
              String replyEncoding = replyMessage.encoding();

              // Send the reply
              request.response().endMessage(replyMessage);
            }).onFailure(err -> {
              request.response()
                .status(GrpcStatus.ABORTED)
                .end();
            });
        });
      } else {
        request.response()
          .status(GrpcStatus.NOT_FOUND)
          .end();
      }
    });
  }

  public void unaryStub1() {
    VertxGreeterGrpcServer.GreeterApi stub = new VertxGreeterGrpcServer.GreeterApi() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
      }
    };
  }

  public void unaryStub2() {
    VertxGreeterGrpcServer.GreeterApi stub = new VertxGreeterGrpcServer.GreeterApi() {
      @Override
      public void sayHello(HelloRequest request, Promise<HelloReply> response) {
        response.complete(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
      }
    };
  }

  public void unaryStub3(VertxGreeterGrpcServer.GreeterApi stub, GrpcServer server) {
    stub.bindAll(server);
  }

  public void streamingRequestStub() {
    VertxStreamingGrpcServer.StreamingApi stub = new VertxStreamingGrpcServer.StreamingApi() {
      @Override
      public void sink(ReadStream<Item> stream, Promise<Empty> response) {
        stream.handler(item -> {
          System.out.println("Process item " + item.getValue());
        });
        // Send response
        stream.endHandler(v -> response.complete(Empty.getDefaultInstance()));
      }
    };
  }

  private ReadStream<Item> streamOfItems() {
    throw new UnsupportedOperationException();
  }

  public void streamingResponseStub1() {
    VertxStreamingGrpcServer.StreamingApi stub = new VertxStreamingGrpcServer.StreamingApi() {
      @Override
      public ReadStream<Item> source(Empty request) {
        return streamOfItems();
      }
    };
  }

  public void streamingResponseStub2() {
    VertxStreamingGrpcServer.StreamingApi stub = new VertxStreamingGrpcServer.StreamingApi() {
      @Override
      public void source(Empty request, WriteStream<Item> response) {
        response.write(Item.newBuilder().setValue("value-1").build());
        response.end(Item.newBuilder().setValue("value-2").build());
      }
    };
  }
}
