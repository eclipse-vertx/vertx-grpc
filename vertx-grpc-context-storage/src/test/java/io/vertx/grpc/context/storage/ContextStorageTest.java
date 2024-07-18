package io.vertx.grpc.context.storage;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Repeat;
import io.vertx.ext.unit.junit.RepeatRule;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServiceBridge;
import io.vertx.iogrpc.server.IoGrpcServer;
import io.vertx.iogrpc.server.IoGrpcServiceBridge;
import org.junit.*;
import org.junit.runner.RunWith;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.UUID;
import java.util.concurrent.Executor;

import static io.grpc.Metadata.*;

@RunWith(VertxUnitRunner.class)
public class ContextStorageTest {

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  private Vertx vertx;
  private volatile HttpServer httpServer;
  private volatile ManagedChannel channel;

  private final Context.Key<String> key = Context.key("test-key");
  private final Context.Key<String> key1 = Context.key("test-key-1");
  private final Context.Key<String> key2 = Context.key("test-key-2");

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext should) {
    if (channel != null) {
      channel.shutdown();
    }
    if (vertx != null) {
      vertx.close().onComplete(should.asyncAssertSuccess());
    }
  }

  @Test
  public void testPropagationAcrossVertxCalls(TestContext should) {
    testPropagationAcrossVertxCalls(should, vertx.getOrCreateContext());
  }

  @Test
  public void testDuplicatePropagationAcrossVertxCalls(TestContext should) {
    testPropagationAcrossVertxCalls(should, ((ContextInternal)vertx.getOrCreateContext()).duplicate());
  }

  private void testPropagationAcrossVertxCalls(TestContext should, io.vertx.core.Context context) {
    Async async = should.async();
    context.runOnContext(v1 -> {
      Context ctx1 = Context.ROOT.withValue(key1, "value-1");
      ctx1.run(() -> {
        should.assertEquals(Context.current(), ctx1);
        vertx.runOnContext(v2 -> {
          should.assertEquals(Context.current(), ctx1);
          should.assertEquals("value-1", key1.get());
          async.complete();
        });
      });
    });
    async.awaitSuccess();
  }

  @Test
  public void testNestedAttach(TestContext should) {
    testNestedAttach(should, vertx.getOrCreateContext());
  }

  @Test
  public void testDuplicateNestedAttach(TestContext should) {
    testNestedAttach(should, ((ContextInternal)vertx.getOrCreateContext()).duplicate());
  }

  private void testNestedAttach(TestContext should, io.vertx.core.Context context) {
    Async async = should.async(2);
    context.runOnContext(v1 -> {
      Context ctx1 = Context.ROOT.withValue(key1, "value-1");
      ctx1.run(() -> {
        Context ctx2 = Context.current().withValue(key2, "value-2");
        ctx2.run(() -> {
          should.assertEquals(Context.current(), ctx2);
          vertx.runOnContext(v2 -> {
            should.assertEquals(Context.current(), ctx2);
            should.assertEquals("value-1", key1.get());
            should.assertEquals("value-2", key2.get());
            async.countDown();
          });
        });
        vertx.runOnContext(v2 -> {
          should.assertEquals(Context.current(), ctx1);
          should.assertEquals("value-1", key1.get());
          should.assertEquals(null, key2.get());
          async.countDown();
        });
      });
    });
    async.awaitSuccess();
  }

  @Test
  public void testNonVertxThread(TestContext should) {
    Context ctx1 = Context.ROOT.withValue(key1, "value-1");
    Context ctx2 = ctx1.withValue(key2, "value-2");
    ctx1.run(() -> {
      should.assertEquals(Context.current(), ctx1);
      should.assertEquals("value-1", key1.get());
      should.assertNull(key2.get());
      ctx2.run(() -> {
        should.assertEquals(Context.current(), ctx2);
        should.assertEquals("value-1", key1.get());
        should.assertEquals("value-2", key2.get());
      });
      should.assertEquals(Context.current(), ctx1);
      should.assertEquals("value-1", key1.get());
      should.assertNull(key2.get());
    });
    should.assertEquals(Context.ROOT, Context.current());
    should.assertNull(key1.get());
    should.assertNull(key2.get());
  }

  @Test
  public void testNestedDuplicate(TestContext should) {
    Async async = should.async();
    io.vertx.core.Context context = ((ContextInternal)vertx.getOrCreateContext()).duplicate();
    context.putLocal("local", "local-value-1");
    context.runOnContext(v1 -> {
      should.assertEquals("local-value-1", context.getLocal("local"));
      Context ctx1 = Context.ROOT.withValue(key1, "value-1");
      ctx1.run(() -> {
        io.vertx.core.Context current = vertx.getOrCreateContext();
        should.assertNotEquals(context, current);
        should.assertEquals("local-value-1", current.getLocal("local"));
        current.putLocal("local", "local-value-2");
      });
      should.assertEquals("local-value-1", context.getLocal("local"));
      async.complete();
    });
    async.awaitSuccess();
  }

  @Test
  public void testPropagateInVertxThread(TestContext should) {
    io.vertx.core.Context context = ((ContextInternal)vertx.getOrCreateContext()).duplicate();
    testPropagateInVertxThread(should, command -> context.runOnContext(v -> command.run()));
  }

  @Test
  public void testPropagateInNonVertxThread(TestContext should) {
    testPropagateInVertxThread(should, Runnable::run);
  }

  private void testPropagateInVertxThread(TestContext should, Executor exec) {
    Async async = should.async();
    Context ctx1 = Context.ROOT.withValue(key, "value-1");
    Context ctx2 = Context.ROOT.withValue(key, "value-2");
    Context ctx3 = Context.ROOT.withValue(key, "value-3");
    exec.execute(() -> {
      ctx1.run(() -> {
        should.assertEquals("value-1", key.get());
        ctx2.run(() -> {
          should.assertEquals("value-2", key.get());
          ctx3.run(() -> {
            should.assertEquals("value-3", key.get());
          });
          should.assertEquals("value-2", key.get());
        });
        should.assertEquals("value-1", key.get());
      });
      async.complete();
    });
    async.awaitSuccess();
  }

  @Test
  @Repeat(10)
  public void testGrpcContextPropagatedAcrossVertxAsyncCalls(TestContext should) {
    CallOptions.Key<String> traceOptionsKey = CallOptions.Key.create("trace");
    Key<String> traceMetadataKey = Key.of("trace", ASCII_STRING_MARSHALLER);
    Context.Key<String> traceContextKey = Context.key("trace");

    GreeterGrpc.GreeterImplBase impl = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        vertx.executeBlocking(() -> {
          Thread.sleep(200);
          return "Hello " + request.getName() + ", trace: " + traceContextKey.get();
        }).onSuccess(greeting -> {
          responseObserver.onNext(HelloReply.newBuilder().setMessage(greeting).build());
          responseObserver.onCompleted();
        }).onFailure(should::fail);
      }
    };

    ServerServiceDefinition def = ServerInterceptors.intercept(impl, new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String traceId = headers.get(traceMetadataKey);
        should.assertNotNull(traceId);
        Context context = Context.current().withValue(traceContextKey, traceId);
        ServerCall.Listener<ReqT> ret;
        try {
          ret = context.call(() -> next.startCall(call, headers));
        } catch (Exception e) {
          // ????
          throw new UndeclaredThrowableException(e);
        }
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(ret) {
          @Override
          public void onHalfClose() {
            context.run(super::onHalfClose);
          }
          @Override
          public void onCancel() {
            context.run(super::onCancel);
          }
          @Override
          public void onReady() {
            context.run(super::onReady);
          }
          @Override
          public void onMessage(ReqT message) {
            context.run(() -> super.onMessage(message));
          }
          @Override
          public void onComplete() {
            context.run(super::onComplete);
          }
        };
      }
    });

    IoGrpcServer server = IoGrpcServer.server(vertx);
    IoGrpcServiceBridge serverStub = IoGrpcServiceBridge.bridge(def);
    serverStub.bind(server);

    Async servertStart = should.async();
    vertx.createHttpServer()
      .requestHandler(server)
      .listen(0).onComplete(should.asyncAssertSuccess(httpServer -> {
        this.httpServer = httpServer;
        servertStart.complete();
      }));
    servertStart.awaitSuccess();

    channel = ManagedChannelBuilder.forAddress("localhost", httpServer.actualPort())
      .usePlaintext()
      .build();

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(ClientInterceptors.intercept(channel, new ClientInterceptor() {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
          return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
              Metadata traceHeaders = new Metadata();
              traceHeaders.put(traceMetadataKey, callOptions.getOption(traceOptionsKey));
              headers.merge(traceHeaders);
              super.start(responseListener, headers);
            }
          };
        }
      }))
      .withCompression("identity");

    String trace = UUID.randomUUID().toString();
    HelloRequest request = HelloRequest.newBuilder().setName("Julien").build();
    HelloReply res = stub.withOption(traceOptionsKey, trace).sayHello(request);

    should.assertEquals("Hello Julien, trace: " + trace, res.getMessage());
  }
}
