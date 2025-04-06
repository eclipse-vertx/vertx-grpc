package examples.grpc;

import static examples.grpc.GreeterGrpc.getServiceDescriptor;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;

import io.grpc.ClientCall;

import io.grpc.stub.StreamObserver;

import io.vertx.grpcio.client.GrpcIoClientChannel;
import io.vertx.grpcio.client.impl.GrpcIoClientImpl;

public final class GreeterGrpcIo {
  private GreeterGrpcIo() {}

   public static GreeterStub newStub(io.vertx.grpcio.client.GrpcIoClient client, io.vertx.core.net.SocketAddress socketAddress) {
    return newStub(new io.vertx.grpcio.client.GrpcIoClientChannel(client, socketAddress));
  }

  public static GreeterStub newStub(io.grpc.Channel channel) {
    return new GreeterStub(channel);
  }


  public static final class GreeterStub extends io.grpc.stub.AbstractStub<GreeterStub> implements GreeterClient {
    private final io.vertx.core.internal.ContextInternal context;
    private GreeterGrpc.GreeterStub delegateStub;

    private GreeterStub(io.grpc.Channel channel) {
      super(channel);
      this.delegateStub = GreeterGrpc.newStub(channel);
      this.context = (io.vertx.core.internal.ContextInternal) ((GrpcIoClientImpl)((GrpcIoClientChannel)getChannel()).client()).vertx().getOrCreateContext();
    }

    private GreeterStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
      delegateStub = GreeterGrpc.newStub(channel).build(channel, callOptions);
      this.context = (io.vertx.core.internal.ContextInternal) ((GrpcIoClientImpl)((GrpcIoClientChannel)getChannel()).client()).vertx().getOrCreateContext();
    }

    @Override
    protected GreeterStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new GreeterStub(channel, callOptions);
    }


    public io.vertx.core.Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request) {
      return io.vertx.grpcio.common.impl.stub.ClientCalls.oneToOne(context, request, delegateStub::sayHello);
    }

  }

  public static io.vertx.grpc.server.Service of(GreeterService service) {
    String compression = null;
    return io.vertx.grpcio.server.GrpcIoServiceBridge.bridge(io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
      .addMethod(
        examples.grpc.GreeterGrpc.getSayHelloMethod(),
        asyncUnaryCall(
                new MethodHandlers<
                        examples.grpc.HelloRequest,
                        examples.grpc.HelloReply>(
                        service, METHODID_SAY_HELLO, compression)))
      .build());
  }

  private static final int METHODID_SAY_HELLO = 0;

  private static final class MethodHandlers<Req, Resp> implements
          io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {

    private final GreeterService serviceImpl;
    private final int methodId;
    private final String compression;

    MethodHandlers(GreeterService serviceImpl, int methodId, String compression) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
      this.compression = compression;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SAY_HELLO:
          io.vertx.grpcio.common.impl.stub.ServerCalls.<examples.grpc.HelloRequest, examples.grpc.HelloReply>oneToOne(
            (io.vertx.core.internal.ContextInternal) io.vertx.core.Vertx.currentContext(),
            (examples.grpc.HelloRequest) request,
            (io.grpc.stub.StreamObserver<examples.grpc.HelloReply>) responseObserver,
            compression,
            serviceImpl::sayHello);
          break;
        default:
          throw new java.lang.AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(io.grpc.stub.StreamObserver<Resp> responseObserver) {
      StreamObserver<Req> reqStreamObserver;
      switch (methodId) {
        default:
          throw new java.lang.AssertionError();
      }
    }
  }
}
