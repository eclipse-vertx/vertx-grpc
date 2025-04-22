package examples.grpc;

import static examples.grpc.StreamingGrpc.getServiceDescriptor;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;

import io.grpc.ClientCall;

import io.grpc.stub.StreamObserver;

import io.vertx.grpcio.client.GrpcIoClientChannel;
import io.vertx.grpcio.client.impl.GrpcIoClientImpl;

/**
 * gRPC/IO client/service in a Vert.x idiomatic way.
 */
public final class StreamingGrpcIo {

  private StreamingGrpcIo() {}

  /**
   * Build a new stub.
   */
  public static StreamingStub newStub(io.vertx.grpcio.client.GrpcIoClient client, io.vertx.core.net.SocketAddress socketAddress) {
    return newStub(((GrpcIoClientImpl)client).vertx(), new io.vertx.grpcio.client.GrpcIoClientChannel(client, socketAddress));
  }

  /**
   * Build a new stub.
   */
  public static StreamingStub newStub(io.vertx.core.Vertx vertx, io.grpc.Channel channel) {
    return new StreamingStub(vertx, channel);
  }

  
  public static final class StreamingStub extends io.grpc.stub.AbstractStub<StreamingStub> implements StreamingClient {
    private final io.vertx.core.internal.ContextInternal context;
    private StreamingGrpc.StreamingStub delegateStub;

    private StreamingStub(io.vertx.core.Vertx vertx, io.grpc.Channel channel) {
      super(channel);
      this.delegateStub = StreamingGrpc.newStub(channel);
      this.context = (io.vertx.core.internal.ContextInternal)vertx.getOrCreateContext();
    }

    private StreamingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
      this.delegateStub = StreamingGrpc.newStub(channel).build(channel, callOptions);
      this.context = (io.vertx.core.internal.ContextInternal) ((GrpcIoClientImpl)((GrpcIoClientChannel)getChannel()).client()).vertx().getOrCreateContext();
    }

    @Override
    protected StreamingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StreamingStub(channel, callOptions);
    }

    
    public io.vertx.core.Future<io.vertx.core.streams.ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request) {
      return io.vertx.grpcio.common.impl.stub.ClientCalls.oneToMany(context, request, delegateStub::source);
    }

    
    public io.vertx.core.Future<examples.grpc.Empty> sink(io.vertx.core.Completable<io.vertx.core.streams.WriteStream<examples.grpc.Item>> handler) {
      return io.vertx.grpcio.common.impl.stub.ClientCalls.manyToOne(context, handler, delegateStub::sink);
    }

    
    public io.vertx.core.Future<io.vertx.core.streams.ReadStream<examples.grpc.Item>> pipe(io.vertx.core.Completable<io.vertx.core.streams.WriteStream<examples.grpc.Item>> handler) {
      return io.vertx.grpcio.common.impl.stub.ClientCalls.manyToMany(context, handler, delegateStub::pipe);
    }
  }

  /**
   * @return a service binding the given {@code service}.
   */
  public static io.grpc.BindableService bindableServiceOf(StreamingService service) {
    return new io.grpc.BindableService() {
      public io.grpc.ServerServiceDefinition bindService() {
        return serverServiceDefinition(service);
      }
    };
  }

  private static io.grpc.ServerServiceDefinition serverServiceDefinition(StreamingService service) {
    String compression = null;
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
      .addMethod(
        examples.grpc.StreamingGrpc.getSourceMethod(),
        asyncServerStreamingCall(
                new MethodHandlers<
                        examples.grpc.Empty,
                        examples.grpc.Item>(
                        service, METHODID_SOURCE, compression)))
      .addMethod(
        examples.grpc.StreamingGrpc.getSinkMethod(),
        asyncClientStreamingCall(
                new MethodHandlers<
                        examples.grpc.Item,
                        examples.grpc.Empty>(
                        service, METHODID_SINK, compression)))
      .addMethod(
        examples.grpc.StreamingGrpc.getPipeMethod(),
        asyncBidiStreamingCall(
                new MethodHandlers<
                        examples.grpc.Item,
                        examples.grpc.Item>(
                        service, METHODID_PIPE, compression)))
      .build();
 }

  private static final int METHODID_SOURCE = 0;
  private static final int METHODID_SINK = 1;
  private static final int METHODID_PIPE = 2;

  private static final class MethodHandlers<Req, Resp> implements
          io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {

    private final StreamingService serviceImpl;
    private final int methodId;
    private final String compression;

    MethodHandlers(StreamingService serviceImpl, int methodId, String compression) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
      this.compression = compression;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SOURCE:
          io.vertx.grpcio.common.impl.stub.ServerCalls.<examples.grpc.Empty, examples.grpc.Item>oneToMany(
            (io.vertx.core.internal.ContextInternal) io.vertx.core.Vertx.currentContext(),
            (examples.grpc.Empty) request,
            (io.grpc.stub.StreamObserver<examples.grpc.Item>) responseObserver,
            compression,
            serviceImpl::source);
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
        case METHODID_SINK:
          reqStreamObserver = (io.grpc.stub.StreamObserver<Req>) io.vertx.grpcio.common.impl.stub.ServerCalls.<examples.grpc.Item, examples.grpc.Empty>manyToOne(
                  (io.vertx.core.internal.ContextInternal) io.vertx.core.Vertx.currentContext(),
                  (io.grpc.stub.StreamObserver<examples.grpc.Empty>) responseObserver,
                  compression,
                  serviceImpl::sink);
          return reqStreamObserver;
        case METHODID_PIPE:
          reqStreamObserver = (io.grpc.stub.StreamObserver<Req>) io.vertx.grpcio.common.impl.stub.ServerCalls.<examples.grpc.Item, examples.grpc.Item>manyToMany(
                  (io.vertx.core.internal.ContextInternal) io.vertx.core.Vertx.currentContext(),
                  (io.grpc.stub.StreamObserver<examples.grpc.Item>) responseObserver,
                  compression,
                  serviceImpl::pipe);
          return reqStreamObserver;
        default:
          throw new java.lang.AssertionError();
      }
    }
  }
}
