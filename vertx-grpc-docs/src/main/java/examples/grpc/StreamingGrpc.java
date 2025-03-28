package examples.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class StreamingGrpc {

  private StreamingGrpc() {}

  public static final java.lang.String SERVICE_NAME = "examples.grpc.Streaming";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<examples.grpc.Empty,
      examples.grpc.Item> getSourceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Source",
      requestType = examples.grpc.Empty.class,
      responseType = examples.grpc.Item.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<examples.grpc.Empty,
      examples.grpc.Item> getSourceMethod() {
    io.grpc.MethodDescriptor<examples.grpc.Empty, examples.grpc.Item> getSourceMethod;
    if ((getSourceMethod = StreamingGrpc.getSourceMethod) == null) {
      synchronized (StreamingGrpc.class) {
        if ((getSourceMethod = StreamingGrpc.getSourceMethod) == null) {
          StreamingGrpc.getSourceMethod = getSourceMethod =
              io.grpc.MethodDescriptor.<examples.grpc.Empty, examples.grpc.Item>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Source"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  examples.grpc.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  examples.grpc.Item.getDefaultInstance()))
              .setSchemaDescriptor(new StreamingMethodDescriptorSupplier("Source"))
              .build();
        }
      }
    }
    return getSourceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<examples.grpc.Item,
      examples.grpc.Empty> getSinkMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Sink",
      requestType = examples.grpc.Item.class,
      responseType = examples.grpc.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
  public static io.grpc.MethodDescriptor<examples.grpc.Item,
      examples.grpc.Empty> getSinkMethod() {
    io.grpc.MethodDescriptor<examples.grpc.Item, examples.grpc.Empty> getSinkMethod;
    if ((getSinkMethod = StreamingGrpc.getSinkMethod) == null) {
      synchronized (StreamingGrpc.class) {
        if ((getSinkMethod = StreamingGrpc.getSinkMethod) == null) {
          StreamingGrpc.getSinkMethod = getSinkMethod =
              io.grpc.MethodDescriptor.<examples.grpc.Item, examples.grpc.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Sink"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  examples.grpc.Item.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  examples.grpc.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new StreamingMethodDescriptorSupplier("Sink"))
              .build();
        }
      }
    }
    return getSinkMethod;
  }

  private static volatile io.grpc.MethodDescriptor<examples.grpc.Item,
      examples.grpc.Item> getPipeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Pipe",
      requestType = examples.grpc.Item.class,
      responseType = examples.grpc.Item.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<examples.grpc.Item,
      examples.grpc.Item> getPipeMethod() {
    io.grpc.MethodDescriptor<examples.grpc.Item, examples.grpc.Item> getPipeMethod;
    if ((getPipeMethod = StreamingGrpc.getPipeMethod) == null) {
      synchronized (StreamingGrpc.class) {
        if ((getPipeMethod = StreamingGrpc.getPipeMethod) == null) {
          StreamingGrpc.getPipeMethod = getPipeMethod =
              io.grpc.MethodDescriptor.<examples.grpc.Item, examples.grpc.Item>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Pipe"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  examples.grpc.Item.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  examples.grpc.Item.getDefaultInstance()))
              .setSchemaDescriptor(new StreamingMethodDescriptorSupplier("Pipe"))
              .build();
        }
      }
    }
    return getPipeMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static StreamingStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<StreamingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<StreamingStub>() {
        @java.lang.Override
        public StreamingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new StreamingStub(channel, callOptions);
        }
      };
    return StreamingStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static StreamingBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<StreamingBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<StreamingBlockingV2Stub>() {
        @java.lang.Override
        public StreamingBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new StreamingBlockingV2Stub(channel, callOptions);
        }
      };
    return StreamingBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static StreamingBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<StreamingBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<StreamingBlockingStub>() {
        @java.lang.Override
        public StreamingBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new StreamingBlockingStub(channel, callOptions);
        }
      };
    return StreamingBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static StreamingFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<StreamingFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<StreamingFutureStub>() {
        @java.lang.Override
        public StreamingFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new StreamingFutureStub(channel, callOptions);
        }
      };
    return StreamingFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void source(examples.grpc.Empty request,
        io.grpc.stub.StreamObserver<examples.grpc.Item> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSourceMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<examples.grpc.Item> sink(
        io.grpc.stub.StreamObserver<examples.grpc.Empty> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getSinkMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<examples.grpc.Item> pipe(
        io.grpc.stub.StreamObserver<examples.grpc.Item> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getPipeMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service Streaming.
   */
  public static abstract class StreamingImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return StreamingGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service Streaming.
   */
  public static final class StreamingStub
      extends io.grpc.stub.AbstractAsyncStub<StreamingStub> {
    private StreamingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected StreamingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StreamingStub(channel, callOptions);
    }

    /**
     */
    public void source(examples.grpc.Empty request,
        io.grpc.stub.StreamObserver<examples.grpc.Item> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getSourceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<examples.grpc.Item> sink(
        io.grpc.stub.StreamObserver<examples.grpc.Empty> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncClientStreamingCall(
          getChannel().newCall(getSinkMethod(), getCallOptions()), responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<examples.grpc.Item> pipe(
        io.grpc.stub.StreamObserver<examples.grpc.Item> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getPipeMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service Streaming.
   */
  public static final class StreamingBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<StreamingBlockingV2Stub> {
    private StreamingBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected StreamingBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StreamingBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, examples.grpc.Item>
        source(examples.grpc.Empty request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getSourceMethod(), getCallOptions(), request);
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<examples.grpc.Item, examples.grpc.Empty>
        sink() {
      return io.grpc.stub.ClientCalls.blockingClientStreamingCall(
          getChannel(), getSinkMethod(), getCallOptions());
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<examples.grpc.Item, examples.grpc.Item>
        pipe() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getPipeMethod(), getCallOptions());
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service Streaming.
   */
  public static final class StreamingBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<StreamingBlockingStub> {
    private StreamingBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected StreamingBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StreamingBlockingStub(channel, callOptions);
    }

    /**
     */
    public java.util.Iterator<examples.grpc.Item> source(
        examples.grpc.Empty request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getSourceMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service Streaming.
   */
  public static final class StreamingFutureStub
      extends io.grpc.stub.AbstractFutureStub<StreamingFutureStub> {
    private StreamingFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected StreamingFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StreamingFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_SOURCE = 0;
  private static final int METHODID_SINK = 1;
  private static final int METHODID_PIPE = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SOURCE:
          serviceImpl.source((examples.grpc.Empty) request,
              (io.grpc.stub.StreamObserver<examples.grpc.Item>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SINK:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.sink(
              (io.grpc.stub.StreamObserver<examples.grpc.Empty>) responseObserver);
        case METHODID_PIPE:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.pipe(
              (io.grpc.stub.StreamObserver<examples.grpc.Item>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSourceMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              examples.grpc.Empty,
              examples.grpc.Item>(
                service, METHODID_SOURCE)))
        .addMethod(
          getSinkMethod(),
          io.grpc.stub.ServerCalls.asyncClientStreamingCall(
            new MethodHandlers<
              examples.grpc.Item,
              examples.grpc.Empty>(
                service, METHODID_SINK)))
        .addMethod(
          getPipeMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              examples.grpc.Item,
              examples.grpc.Item>(
                service, METHODID_PIPE)))
        .build();
  }

  private static abstract class StreamingBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    StreamingBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return examples.grpc.Docs.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Streaming");
    }
  }

  private static final class StreamingFileDescriptorSupplier
      extends StreamingBaseDescriptorSupplier {
    StreamingFileDescriptorSupplier() {}
  }

  private static final class StreamingMethodDescriptorSupplier
      extends StreamingBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    StreamingMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (StreamingGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new StreamingFileDescriptorSupplier())
              .addMethod(getSourceMethod())
              .addMethod(getSinkMethod())
              .addMethod(getPipeMethod())
              .build();
        }
      }
    }
    return result;
  }
}
