package io.vertx.grpc.it.tests;

import io.grpc.examples.helloworld.*;
import io.grpc.examples.streamingtranscoding.StreamingHelloReply;
import io.grpc.examples.streamingtranscoding.StreamingHelloRequest;
import io.grpc.examples.streamingtranscoding.StreamingTranscodingGreeterClient;
import io.grpc.examples.streamingtranscoding.StreamingTranscodingGreeterGrpcClient;
import io.grpc.examples.streamingtranscoding.StreamingTranscodingGreeterGrpcService;
import io.grpc.examples.streamingtranscoding.StreamingTranscodingGreeterService;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpcio.server.GrpcIoServer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TranscodingTest extends ProxyTestBase {

  @Test
  public void testUnaryBasic() throws TimeoutException {
    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/Julien").setMethod(HttpMethod.GET);
    testUnaryBasic(server -> server.callHandler(GreeterGrpcService.SayHello, call -> call.handler(helloRequest -> {
      HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
      call.response().end(helloReply);
    })), options, Buffer.buffer());
  }

  @Test
  public void testIoUnaryBasic() throws TimeoutException {
    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/helloworld.Greeter/SayHello").setMethod(HttpMethod.POST);
    testUnaryBasic(server -> server.callHandler(GreeterGrpc.getSayHelloMethod(), call -> call.handler(helloRequest -> {
      HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
      call.response().end(helloReply);
    })), options, Buffer.buffer(new JsonObject().put("name", "Julien").encode()));
  }

  public void testUnaryBasic(Consumer<GrpcIoServer> setup, RequestOptions request, Buffer requestBody) throws TimeoutException {
    HttpClient client = vertx.createHttpClient();
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    setup.accept(grpcServer);

    vertx.createHttpServer().requestHandler(grpcServer).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    Buffer body = client.request(request).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send(requestBody);
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(body.toString()));
  }

  @Test
  public void testUnaryBasicReversed() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHelloAgain, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v2/hello/Julien").setMethod(HttpMethod.GET);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(body.toString()));
  }

  @Test
  public void testUnaryBasicUnknownPath() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHello, call -> {
        call.handler(helloRequest -> {
          HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/unknown").setMethod(HttpMethod.GET);

    int statusCode = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send();
      }).await(10, TimeUnit.SECONDS)
      .statusCode();
    assertEquals(500, statusCode);
  }

  @Test
  public void testUnaryAdditionalBindings() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHello, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello").setMethod(HttpMethod.POST);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send(createRequest("Julien"));
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(body.toString()));
  }

  @Test
  public void testUnaryAdditionalBindingsUnknownPath() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHello, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/unknown").setMethod(HttpMethod.POST);

    int statusCode = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send(createRequest("Julien"));
      }).await(10, TimeUnit.SECONDS)
      .statusCode();
    assertEquals(500, statusCode);
  }

  @Test
  public void testUnaryInvalidBody() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHello, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello").setMethod(HttpMethod.POST);

    int statusCode = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send("invalid");
      }).await(10, TimeUnit.SECONDS)
      .statusCode();
    assertEquals(400, statusCode);
  }

  @Test
  public void testUnaryCustomMethod() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHelloCustom, call -> {
        call.handler(helloRequest -> {
          HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/custom/Julien").setMethod(HttpMethod.ACL);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(body.toString()));
  }

  @Test
  public void testUnaryWithRequestBody() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHelloWithBody, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getRequest().getName()).build();
        call.response().end(helloReply);
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/body").setMethod(HttpMethod.POST);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send(createRequest("Julien"));
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(body.toString()));
  }

  @Test
  public void testUnaryWithNestedPath() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHelloNested, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/rooms/test/messages/Julien").setMethod(HttpMethod.POST);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    String[] path = getMessage(body.toString()).split("/");
    assertEquals(4, path.length);
    String name = path[3];
    assertEquals("Hello Julien", "Hello " + name);
  }

  @Test
  public void testUnaryWithResponseBody() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHelloWithResponseBOdy, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        HelloBodyResponse helloBodyResponse = HelloBodyResponse.newBuilder().setResponse(helloReply).build();
        call.response().end(helloBodyResponse);
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/body/response").setMethod(HttpMethod.POST);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send(createRequest("Julien"));
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(body.toString()));
  }

  @Test
  public void testUnaryWithoutOption() throws TimeoutException {
    testUnaryWithoutOption(server -> server.callHandler(GreeterGrpcService.SayHelloWithoutOptions, call -> call.handler(helloRequest -> {
      HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
      call.response().end(helloReply);
    })));
  }

  @Test
  public void testIoUnaryWithoutOption1() throws TimeoutException {
    testUnaryWithoutOption(server -> server.callHandler(GreeterGrpc.getSayHelloWithoutOptionsMethod(), call -> call.handler(helloRequest -> {
      HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
      call.response().end(helloReply);
    })));
  }

  @Test
  public void testIoUnaryWithoutOption2() throws TimeoutException {
    testUnaryWithoutOption(server -> server.addService(new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHelloWithoutOptions(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
        responseObserver.onNext(helloReply);
        responseObserver.onCompleted();
      }
    }));
  }

  private void testUnaryWithoutOption(Consumer<GrpcIoServer> wirer) throws TimeoutException {
    HttpClient client = vertx.createHttpClient();
    GrpcIoServer grpcServer = GrpcIoServer.server(vertx);

    wirer.accept(grpcServer);

    vertx.createHttpServer().requestHandler(grpcServer).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/helloworld.Greeter/SayHelloWithoutOptions").setMethod(HttpMethod.POST);

    Buffer res = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send(createRequest("Julien"));
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON).compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(res.toString()));
  }

  @Test
  public void testUnaryCollisionWithoutOption() throws TimeoutException {
    HttpClient httpClient = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHelloWithoutOptions, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/helloworld.Greeter/SayHelloWithoutOptions").setMethod(HttpMethod.POST);

    Buffer httpBody = httpClient.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send(createRequest("Julien"));
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(httpBody.toString()));

    GrpcClient grpcClient = GrpcClient.client(vertx);
    GreeterGrpcClient greeterClient = GreeterGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(8080, "localhost"));

    HelloReply reply = greeterClient.sayHelloWithoutOptions(HelloRequest.newBuilder().setName("Julien").build()).await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", reply.getMessage());
  }

  @Test
  public void testUnaryAddService() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).addService(GreeterGrpcService.of(new GreeterService() {
        @Override
        public Future<HelloReply> sayHello(HelloRequest request) {
          return Future.succeededFuture(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        }
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/Julien").setMethod(HttpMethod.GET);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .expecting(HttpResponseExpectation.JSON)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", getMessage(body.toString()));
  }

  @Test
  public void testServerStreamingAddService() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).addService(StreamingTranscodingGreeterGrpcService.of(new StreamingTranscodingGreeterService() {
        @Override
        protected void sayHelloStreaming(StreamingHelloRequest request, WriteStream<StreamingHelloReply> response) {
          response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 1").build());
          response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 2").build());
          response.end();
        }
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/stream/Julien").setMethod(HttpMethod.GET);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);

    JsonArray array = new JsonArray(body);
    assertEquals(2, array.size());
    assertEquals("Hello Julien 1", array.getJsonObject(0).getString("message"));
    assertEquals("Hello Julien 2", array.getJsonObject(1).getString("message"));
  }

  @Test
  public void testServerStreaming() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(StreamingTranscodingGreeterGrpcService.SayHelloStreaming, call -> call.handler(request -> {
        GrpcServerResponse<StreamingHelloRequest, StreamingHelloReply> response = call.response();
        response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 1").build());
        response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 2").build());
        response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 3").build());
        response.end();
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/stream/Julien").setMethod(HttpMethod.GET);

    HttpClientResponse response = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .compose(resp -> resp.body().map(resp))
      .await(10, TimeUnit.SECONDS);

    assertTrue(response.headers().contains(HttpHeaders.CONTENT_TYPE, "application/json", true));
    // Streaming responses are chunked, so the length is not known up-front.
    assertFalse(response.headers().contains(HttpHeaders.CONTENT_LENGTH));
    JsonArray array = new JsonArray(response.body().result());
    assertEquals(3, array.size());
    assertEquals("Hello Julien 1", array.getJsonObject(0).getString("message"));
    assertEquals("Hello Julien 2", array.getJsonObject(1).getString("message"));
    assertEquals("Hello Julien 3", array.getJsonObject(2).getString("message"));
  }

  @Test
  public void testServerStreamingNdjson() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(StreamingTranscodingGreeterGrpcService.SayHelloStreaming, call -> call.handler(request -> {
        GrpcServerResponse<StreamingHelloRequest, StreamingHelloReply> response = call.response();
        response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 1").build());
        response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 2").build());
        response.end();
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/stream/Julien").setMethod(HttpMethod.GET);

    HttpClientResponse response = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/x-ndjson");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .compose(resp -> resp.body().map(resp))
      .await(10, TimeUnit.SECONDS);

    assertTrue(response.headers().contains(HttpHeaders.CONTENT_TYPE, "application/x-ndjson", true));
    String[] lines = response.body().result().toString().split("\n");
    assertEquals(2, lines.length);
    assertEquals("Hello Julien 1", new JsonObject(lines[0]).getString("message"));
    assertEquals("Hello Julien 2", new JsonObject(lines[1]).getString("message"));
  }

  @Test
  public void testServerStreamingSse() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(StreamingTranscodingGreeterGrpcService.SayHelloStreaming, call -> call.handler(request -> {
        GrpcServerResponse<StreamingHelloRequest, StreamingHelloReply> response = call.response();
        response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 1").build());
        response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName() + " 2").build());
        response.end();
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/stream/Julien").setMethod(HttpMethod.GET);

    HttpClientResponse response = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "text/event-stream");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .compose(resp -> resp.body().map(resp))
      .await(10, TimeUnit.SECONDS);

    assertTrue(response.headers().contains(HttpHeaders.CONTENT_TYPE, "text/event-stream", true));
    String[] events = response.body().result().toString().split("\n\n");
    assertEquals(2, events.length);
    assertTrue(events[0].startsWith("data: "));
    assertTrue(events[1].startsWith("data: "));
    assertEquals("Hello Julien 1", new JsonObject(events[0].substring("data: ".length())).getString("message"));
    assertEquals("Hello Julien 2", new JsonObject(events[1].substring("data: ".length())).getString("message"));
  }

  @Test
  public void testServerStreamingEmpty() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(StreamingTranscodingGreeterGrpcService.SayHelloStreaming, call -> call.handler(request -> {
        GrpcServerResponse<StreamingHelloRequest, StreamingHelloReply> response = call.response();
        response.end();
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/stream/Julien").setMethod(HttpMethod.GET);

    Buffer body = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);

    assertEquals(0, new JsonArray(body).size());
  }

  @Test
  public void testServerStreamingGrpcCollision() throws TimeoutException {
    HttpClient httpClient = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(StreamingTranscodingGreeterGrpcService.SayHelloStreaming, call -> call.handler(request -> {
        GrpcServerResponse<StreamingHelloRequest, StreamingHelloReply> response = call.response();
        response.write(StreamingHelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        response.end();
      }))).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/stream/Julien").setMethod(HttpMethod.GET);

    Buffer httpBody = httpClient.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send();
      }).expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await(10, TimeUnit.SECONDS);
    assertEquals("Hello Julien", new JsonArray(httpBody).getJsonObject(0).getString("message"));

    // The same service method is still reachable over plain gRPC.
    GrpcClient grpcClient = GrpcClient.client(vertx);
    StreamingTranscodingGreeterClient greeterClient = StreamingTranscodingGreeterGrpcClient.create(grpcClient, SocketAddress.inetSocketAddress(8080, "localhost"));

    List<String> received = greeterClient
      .sayHelloStreaming(StreamingHelloRequest.newBuilder().setName("Julien").build())
      .compose(stream -> {
        Promise<List<String>> promise = Promise.promise();
        List<String> replies = new ArrayList<>();
        stream.handler(reply -> replies.add(reply.getMessage()));
        stream.endHandler(v -> promise.tryComplete(replies));
        stream.exceptionHandler(promise::tryFail);
        return promise.future();
      })
      .await(10, TimeUnit.SECONDS);
    assertEquals(Collections.singletonList("Hello Julien"), received);
  }

  @Test
  public void testUnknownService() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();

    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx)).listen(8080, "localhost").await(10, TimeUnit.SECONDS);

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello").setMethod(HttpMethod.POST);

    int statusCode = client.request(options).compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        req.putHeader(HttpHeaders.ACCEPT, "application/json");
        return req.send(Buffer.buffer(new JsonObject().put("name", "Julien").encode()));
      }).await(10, TimeUnit.SECONDS)
      .statusCode();
    assertEquals(500, statusCode);
  }

  private String createRequest(String name) {
    return Json.encode(new JsonObject().put("name", name));
  }

  private String getMessage(String message) {
    return Json.decodeValue(message, Map.class).get("message").toString();
  }
}
