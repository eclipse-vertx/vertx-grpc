package io.vertx.grpc.it;

import io.grpc.*;
import io.grpc.examples.helloworld.*;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.StatusException;
import io.vertx.grpcio.client.GrpcIoClientChannel;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import io.vertx.tests.common.GrpcTestBase;
import io.vertx.tests.common.grpc.TestServiceGrpc;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class EventBusTest extends GrpcTestBase {

  private static final GreeterGrpcService greeterService = new GreeterGrpcService() {
    @Override
    public Future<HelloReply> sayHello(HelloRequest request) {
      return Future.succeededFuture(HelloReply.newBuilder()
        .setMessage("Hello " + request.getName())
        .build());
    }
  };

  private EventBusGrpcClient client;
  private EventBusGrpcServer server;

  @Override
  @Before
  public void setUp(TestContext should) {
    super.setUp(should);

    client = EventBusGrpcClient.create(vertx);
    server = EventBusGrpcServer.create(vertx);
  }

  @Test
  public void testUnary() throws Exception {

    server.addService(greeterService);
    GreeterClient greeter = GreeterGrpcClient.create(client);

    HelloReply reply = greeter
      .sayHello(HelloRequest.newBuilder().setName("Julien").build())
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryJson() throws Exception {

    server.addService(greeterService);
    GreeterClient jsonGreeter = GreeterGrpcClient.create(client, WireFormat.JSON);

    HelloReply reply = jsonGreeter
      .sayHello(HelloRequest.newBuilder().setName("Julien").build())
      .await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryErrorHandling() throws Exception {
    EventBusGrpcServer errorServer = EventBusGrpcServer.create(vertx);
    errorServer.addService(GreeterGrpcService.of(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        throw new RuntimeException("Simulated error");
      }
    }));

    GreeterClient greeter = GreeterGrpcClient.create(client);

    try {
      greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build())
        .await(10, TimeUnit.SECONDS);
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.UNKNOWN, e.actualStatus());
    }
  }

  @Test
  public void testUnaryStatusException() throws Exception {
    EventBusGrpcServer errorServer = EventBusGrpcServer.create(vertx);
    errorServer.addService(GreeterGrpcService.of(new GreeterService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.failedFuture(new StatusException(GrpcStatus.NOT_FOUND, "Not found"));
      }
    }));

    GreeterClient greeter = GreeterGrpcClient.create(client);
    try {
      greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build()).await(10, TimeUnit.SECONDS);
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.NOT_FOUND, e.actualStatus());
    }
  }

  @Test
  public void testServerClose() throws Exception {

    server.addService(greeterService);
    GreeterClient greeter = GreeterGrpcClient.create(client);

    HelloReply reply = greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build()).await(10, TimeUnit.SECONDS);

    assertEquals("Hello Julien", reply.getMessage());

    Promise<Void> closePromise = Promise.promise();
    server.close(closePromise);
    closePromise.future().await(10, TimeUnit.SECONDS);

    try {
      greeter.sayHello(HelloRequest.newBuilder().setName("Julien").build())
        .await(10, TimeUnit.SECONDS);
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.UNAVAILABLE, e.actualStatus());
    }
  }

  @Test
  public void testGrpcIO() throws Exception {

    GreeterGrpc.GreeterImplBase service = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    Metadata.Key<String> header = Metadata.Key.of("the-header", Metadata.ASCII_STRING_MARSHALLER);

    AtomicReference<String> requestHeader = new AtomicReference<>();
    AtomicReference<String> responseHeader = new AtomicReference<>();
    AtomicReference<String> responseTrailer = new AtomicReference<>();

    ServerServiceDefinition def = ServerInterceptors.intercept(service, new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        requestHeader.set(headers.get(header));
        return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
          @Override
          public void sendHeaders(Metadata headers) {
            headers.put(header, "header_response_value");
            super.sendHeaders(headers);
          }
          @Override
          public void close(Status status, Metadata trailers) {
            trailers.put(header, "trailer_response_value");
            super.close(status, trailers);
          }
        }, headers);
      }
    });

    server.addService(GrpcIoServiceBridge.bridge(def));

    ClientInterceptor interceptor = new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            headers.put(header, "header_request_value");
            super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override
              public void onHeaders(Metadata headers) {
                responseHeader.set(headers.get(header));
                super.onHeaders(headers);
              }
              @Override
              public void onClose(Status status, Metadata trailers) {
                responseTrailer.set(trailers.get(header));
                super.onClose(status, trailers);
              }
            }, headers);
          }
        };
      }
    };

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(ClientInterceptors.intercept(new GrpcIoClientChannel(client), interceptor));
    HelloReply reply = stub.sayHello(HelloRequest.newBuilder().setName("Julien").build());
    assertEquals("Hello Julien", reply.getMessage());
    assertEquals("header_request_value", requestHeader.get());
    assertEquals("header_response_value", responseHeader.get());
    assertEquals("trailer_response_value", responseTrailer.get());
  }
}
