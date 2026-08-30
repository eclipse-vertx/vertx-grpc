/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.it.tests;

import io.grpc.examples.helloworld.GreeterGrpcClient;
import io.grpc.examples.helloworld.GreeterGrpcService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.impl.GrpcClientImpl;
import io.vertx.grpc.client.impl.GrpcClientInvoker;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.MethodCardinality;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.impl.GrpcStream;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.eventbus.impl.EventBusGrpcClientEndpoint;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.impl.GrpcServerImpl;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyTest extends ProxyTestBase {

  @Test
  public void testUnaryApplicationProxy(TestContext should) {

    GrpcClient client = GrpcClient.client(vertx);

    Future<HttpServer> server = vertx.createHttpServer().requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHello, call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      });
    })).listen(8080, "localhost");

    Future<HttpServer> proxy = vertx.createHttpServer().requestHandler(GrpcServer.server(vertx).callHandler(clientReq -> {
      clientReq.pause();
      client.request(SocketAddress.inetSocketAddress(8080, "localhost")).onComplete(should.asyncAssertSuccess(proxyReq -> {
        proxyReq.response().onSuccess(resp -> {
          GrpcServerResponse<Buffer, Buffer> bc = clientReq.response();
          resp.messageHandler(bc::writeMessage);
          resp.endHandler(v -> bc.end());
        });
        proxyReq.fullMethodName(clientReq.fullMethodName());
        clientReq.messageHandler(proxyReq::writeMessage);
        clientReq.endHandler(v -> proxyReq.end());
        clientReq.resume();
      }));
    })).listen(8081, "localhost");

    Async test = should.async();
    server.flatMap(v -> proxy).onComplete(should.asyncAssertSuccess(v -> {
      client.request(SocketAddress.inetSocketAddress(8081, "localhost"), GreeterGrpcClient.SayHello)
        .onComplete(should.asyncAssertSuccess(callRequest -> {
          callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
            AtomicInteger count = new AtomicInteger();
            callResponse.handler(reply -> {
              should.assertEquals(1, count.incrementAndGet());
              should.assertEquals("Hello Julien", reply.getMessage());
            });
            callResponse.endHandler(v2 -> {
              should.assertEquals(1, count.get());
              test.complete();
            });
          }));
          callRequest.end(HelloRequest.newBuilder().setName("Julien").build());
        }));
    }));
  }

  @Test
  public void testUnaryTransportProxy(TestContext should) {

    GrpcClientImpl client = (GrpcClientImpl)GrpcClient.client(vertx);

    HttpServer server = vertx.createHttpServer()
      .requestHandler(GrpcServer.server(vertx).callHandler(GreeterGrpcService.SayHello, call -> {
        call.handler(helloRequest -> {
          HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
          call.response().end(helloReply);
        });
      }))
      .listen(8080, "localhost")
      .await();

    GrpcServerImpl s = (GrpcServerImpl) GrpcServer.server(vertx);
    s.streamHandler(call -> {
      Future<GrpcClientInvoker> f = client.connect(new RequestOptions().setServer(SocketAddress.inetSocketAddress(8080, "localhost")));
      f.onComplete(ar -> {
        if (ar.succeeded()) {
          GrpcClientInvoker invoker = ar.result();
          GrpcStream outbound = invoker.invoke(call.serviceName(), call.methodName());
          GrpcStream inbound = call.stream();
          inbound.handler(outbound::write);
          inbound.endHandler(v -> outbound.end());
          outbound.handler(inbound::write);
          outbound.endHandler(v -> inbound.end());
          inbound.exceptionHandler(err -> {
          });
          outbound.exceptionHandler(err -> {
          });
          inbound.resume();
        } else {
          // Handle me
        }
      });
    });

    HttpServer proxy = vertx.createHttpServer()
      .requestHandler(s)
      .listen(8081, "localhost")
      .await();

    Async test = should.async();
    client.request(SocketAddress.inetSocketAddress(8081, "localhost"), GreeterGrpcClient.SayHello)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.handler(reply -> {
            should.assertEquals(1, count.incrementAndGet());
            should.assertEquals("Hello Julien", reply.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(1, count.get());
            test.complete();
          });
        }));
        callRequest.end(HelloRequest.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testUnaryHttpToEventBusProxy(TestContext should) {

    EventBusGrpcServer server = EventBusGrpcServer
      .server(vertx)
      .await();

    server.callHandler(GreeterGrpcService.SayHello, call -> {
      call.handler(helloRequest -> {
        HelloReply helloReply = HelloReply.newBuilder().setMessage("Hello " + helloRequest.getName()).build();
        call.response().end(helloReply);
      });
    });

    EventBusGrpcClientEndpoint grpcClient = (EventBusGrpcClientEndpoint)EventBusGrpcClient.client(vertx).await();

    GrpcServerImpl s = (GrpcServerImpl) GrpcServer.server(vertx);
    s.streamHandler(call -> {

      // Since we forward a request without knowledge of the service cardinality, we use streaming
      ServiceMethod<Buffer, Buffer> serviceMethod = ServiceMethod.client(
        call.serviceName(),
        call.methodName(),
        MethodCardinality.BIDI_STREAMING,
        GrpcMessageEncoder.IDENTITY,
        GrpcMessageDecoder.IDENTITY
      );

      Future<GrpcClientInvoker> f = grpcClient.connect(serviceMethod);
      f.onComplete(ar -> {
        if (ar.succeeded()) {
          GrpcClientInvoker invoker = ar.result();
          GrpcStream outbound = invoker.invoke(call.serviceName(), call.methodName());
          GrpcStream inbound = call.stream();
          inbound.handler(outbound::write);
          inbound.endHandler(v -> outbound.end());
          outbound.handler(inbound::write);
          outbound.endHandler(v -> inbound.end());
          inbound.exceptionHandler(err -> {
          });
          outbound.exceptionHandler(err -> {
          });
          inbound.resume();
        } else {
          // Handle me
        }
      });
    });

    HttpServer proxy = vertx.createHttpServer()
      .requestHandler(s)
      .listen(8081, "localhost")
      .await();

    GrpcClientImpl client = (GrpcClientImpl)GrpcClient.client(vertx);

    Async test = should.async();
    client.request(SocketAddress.inetSocketAddress(8081, "localhost"), GreeterGrpcClient.SayHello)
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.handler(reply -> {
            should.assertEquals(1, count.incrementAndGet());
            should.assertEquals("Hello Julien", reply.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(1, count.get());
            test.complete();
          });
        }));
        callRequest.end(HelloRequest.newBuilder().setName("Julien").build());
      }));
  }
}
