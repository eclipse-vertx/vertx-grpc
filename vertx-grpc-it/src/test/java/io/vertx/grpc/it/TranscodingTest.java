package io.vertx.grpc.it;

import io.grpc.examples.helloworld.GreeterGrpcService;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServer;
import org.junit.Test;

import java.util.Map;

public class TranscodingTest extends ProxyTestBase {

  @Test
  public void testUnaryBasic(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHello, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/Julien").setMethod(HttpMethod.GET);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send();
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        should.assertEquals("Hello Julien", getMessage(body.toString()));
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryBasicReversed(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHelloAgain, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v2/hello/Julien").setMethod(HttpMethod.GET);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Accept", "application/json");
        req.putHeader("Content-Type", "application/json");
        return req.send();
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        should.assertEquals("Hello Julien", getMessage(body.toString()));
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryBasicUnknownPath(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHello, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/unknown").setMethod(HttpMethod.GET);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send();
      }).compose(resp -> {
        should.assertEquals(500, resp.statusCode());
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryAdditionalBindings(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHello, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello").setMethod(HttpMethod.POST);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send(createRequest("Julien"));
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        should.assertEquals("Hello Julien", getMessage(body.toString()));
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryAdditionalBindingsUnknownPath(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHello, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/unknown").setMethod(HttpMethod.POST);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send(createRequest("Julien"));
      }).compose(resp -> {
        should.assertEquals(500, resp.statusCode());
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryInvalidBody(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHello, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello").setMethod(HttpMethod.POST);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send("invalid");
      }).compose(resp -> {
        should.assertEquals(400, resp.statusCode());
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryCustomMethod(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHelloCustom, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/custom/Julien").setMethod(HttpMethod.ACL);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send();
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        should.assertEquals("Hello Julien", getMessage(body.toString()));
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryWithRequestBody(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHelloWithBody, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getRequest().getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/body").setMethod(HttpMethod.POST);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send(createRequest("Julien"));
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        should.assertEquals("Hello Julien", getMessage(body.toString()));
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryWithNestedPath(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHelloNested, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/rooms/test/messages/Julien").setMethod(HttpMethod.POST);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send();
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        String[] path = getMessage(body.toString()).split("/");
        should.assertEquals(4, path.length);
        String name = path[3];
        should.assertEquals("Hello Julien", "Hello " + name);
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryWithResponseBody(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHelloWithResponseBOdy, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          io.grpc.examples.helloworld.HelloBodyResponse helloBodyResponse = io.grpc.examples.helloworld.HelloBodyResponse.newBuilder().setResponse(helloReply)
            .build();
          call.response().end(helloBodyResponse);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/body/response").setMethod(HttpMethod.POST);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send(createRequest("Julien"));
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        should.assertEquals("Hello Julien", getMessage(body.toString()));
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryWithoutOption(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHelloWithoutOptions, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/helloworld.Greeter/SayHelloWithoutOptions").setMethod(HttpMethod.POST);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send(createRequest("Julien"));
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        should.assertEquals("Hello Julien", getMessage(body.toString()));
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  @Test
  public void testUnaryCollisionWithoutOption(TestContext should) {
    HttpClient client = vertx.createHttpClient();

    Future<HttpServer> server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.Transcoding.SayHelloWithoutOptions, call -> {
        call.handler(helloRequest -> {
          io.grpc.examples.helloworld.HelloReply helloReply = io.grpc.examples.helloworld.HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName())
            .build();
          call.response().end(helloReply);
        });
      }).callHandler(GreeterGrpcService.SayHelloWithoutOptions, call -> {
        call.handler(helloRequest -> call.response().status(GrpcStatus.INTERNAL).end());
      })).listen(8080, "localhost");

    RequestOptions options = new RequestOptions().setHost("localhost").setPort(8080).setURI("/v1/hello/Julien").setMethod(HttpMethod.POST);

    Async test = should.async();

    server.onComplete(should.asyncAssertSuccess(v -> {
      client.request(options).compose(req -> {
        req.putHeader("Content-Type", "application/json");
        req.putHeader("Accept", "application/json");
        return req.send(createRequest("Julien"));
      }).compose(resp -> {
        should.assertEquals(200, resp.statusCode());
        should.assertEquals("application/json", resp.getHeader("Content-Type"));
        return resp.body();
      }).onComplete(should.asyncAssertSuccess(body -> {
        should.assertEquals("Hello Julien", getMessage(body.toString()));
        test.complete();
      }));
    }));

    test.awaitSuccess();
  }

  private String createRequest(String name) {
    return Json.encode(new JsonObject().put("name", name));
  }

  private String getMessage(String message) {
    return Json.decodeValue(message, Map.class).get("message").toString();
  }
}
