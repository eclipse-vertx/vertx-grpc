package io.vertx.grpc.it;

import io.grpc.examples.helloworld.*;
import io.grpc.stub.StreamObserver;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpcio.server.GrpcIoServer;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

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
  public void testUnaryWithEmptyMessage() throws TimeoutException {
    HttpClient client = vertx.createHttpClient();
    vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHelloEmpty, call -> call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello there").build();
        call.response().end(helloReply);
      }))).listen(18080, "localhost").await(10, TimeUnit.SECONDS);

  RequestOptions options = new RequestOptions().setHost("localhost").setPort(18080).setURI("/v1/hello").setMethod(HttpMethod.GET);

  Buffer body = client.request(options).compose(req -> {
      req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
      req.putHeader(HttpHeaders.ACCEPT, "application/json");
      return req.send("");
    }).expecting(HttpResponseExpectation.SC_OK)
    .expecting(HttpResponseExpectation.JSON)
    .compose(HttpClientResponse::body)
    .await(10, TimeUnit.SECONDS);
    assertEquals("Hello there", getMessage(body.toString()));
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
