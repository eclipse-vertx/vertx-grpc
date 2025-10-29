package io.vertx.grpcio.client;

import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.Compressor;
import io.grpc.Decompressor;
import io.grpc.DecompressorRegistry;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.Status;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.common.GrpcErrorException;
import io.vertx.grpc.client.impl.GrpcClientRequestImpl;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpcio.common.impl.BridgeMessageDecoder;
import io.vertx.grpcio.common.impl.BridgeMessageEncoder;
import io.vertx.grpcio.common.impl.ReadStreamAdapter;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class VertxClientCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {

  private final GrpcIoClient client;
  private final SocketAddress server;
  private final Executor exec;
  private final MethodDescriptor<RequestT, ResponseT> methodDescriptor;
  private final String encoding;
  private final Compressor compressor;
  private final Deadline deadline;
  private Future<GrpcClientRequest<RequestT, ResponseT>> fut;
  private Listener<ResponseT> listener;
  private WriteStreamAdapter<RequestT> writeAdapter;
  private ReadStreamAdapter<ResponseT> readAdapter;
  private GrpcClientRequest<RequestT, ResponseT> request;
  private GrpcClientResponse<RequestT, ResponseT> grpcResponse;

  VertxClientCall(GrpcIoClient client,
                  SocketAddress server,
                  Executor exec,
                  MethodDescriptor<RequestT, ResponseT> methodDescriptor,
                  String encoding,
                  Compressor compressor,
                  Deadline deadline) {
    this.client = client;
    this.server = server;
    this.exec = exec;
    this.methodDescriptor = methodDescriptor;
    this.encoding = encoding;
    this.compressor = compressor;
    this.deadline = deadline;
    writeAdapter = new WriteStreamAdapter<RequestT>() {
      @Override
      protected void handleReady() {
        listener.onReady();
      }
    };
    readAdapter = new ReadStreamAdapter<ResponseT>() {
      @Override
      protected void handleMessage(ResponseT msg) {
        if (exec == null) {
          listener.onMessage(msg);
        } else {
          exec.execute(() -> listener.onMessage(msg));
        }
      }
    };
  }

  @Override
  public boolean isReady() {
    return writeAdapter.isReady();
  }

  @Override
  public void start(Listener<ResponseT> responseListener, Metadata headers) {
    listener = responseListener;
    fut = client.request(server, methodDescriptor);
    fut.onComplete(ar1 -> {
      if (ar1.succeeded()) {
        request = ar1.result();
        io.vertx.grpcio.common.impl.Utils.writeMetadata(headers, request.headers());
        ScheduledFuture<?> sf;
        if (deadline != null) {
          long timeout = deadline.timeRemaining(TimeUnit.MILLISECONDS);
          request.timeout(timeout, TimeUnit.MILLISECONDS);
          sf = deadline.runOnExpiration(() -> request.cancel(), new VertxScheduledExecutorService(((GrpcClientRequestImpl)request).context()));
        } else {
          sf = null;
        }
        if (encoding != null) {
          request.encoding(encoding);
        }
        Future<GrpcClientResponse<RequestT, ResponseT>> responseFuture = request.response();
        responseFuture.onComplete(ar2 -> {
          if (ar2.succeeded()) {

            grpcResponse = ar2.result();

            if (sf != null) {
              grpcResponse.end().onComplete(ar -> {
                sf.cancel(false);
              });
            }

            String respEncoding = grpcResponse.encoding();
            Decompressor decompressor = DecompressorRegistry.getDefaultInstance().lookupDecompressor(respEncoding);

            BridgeMessageDecoder<ResponseT> decoder = new BridgeMessageDecoder<>(methodDescriptor.getResponseMarshaller(), decompressor);

            Metadata responseHeaders = io.vertx.grpcio.common.impl.Utils.readMetadata(grpcResponse.headers());
            if (exec == null) {
              responseListener.onHeaders(responseHeaders);
            } else {
              exec.execute(() -> {
                responseListener.onHeaders(responseHeaders);
              });
            }
            readAdapter.init(grpcResponse, decoder);
            grpcResponse.end().onComplete(ar -> {
              Status status;
              Metadata trailers;
              if (grpcResponse.status() != null) {
                status = Status.fromCodeValue(grpcResponse.status().code);
                if (grpcResponse.statusMessage() != null) {
                  status = status.withDescription(grpcResponse.statusMessage());
                }
                MultiMap responseTrailers;
                boolean trailersOnly = grpcResponse.trailers().isEmpty();
                if (trailersOnly) {
                  responseTrailers = grpcResponse.headers();
                } else {
                  responseTrailers = grpcResponse.trailers();
                }
                trailers = io.vertx.grpcio.common.impl.Utils.readMetadata(responseTrailers);
              } else {
                status = Status.fromThrowable(ar.cause());
                trailers = new Metadata();
              }
              doClose(status, trailers);
            });
          } else {
            Throwable err = ar2.cause();
            if (err instanceof GrpcErrorException) {
              GrpcErrorException reset = (GrpcErrorException) err;
              doClose(Status.fromCodeValue(reset.status().code), new Metadata());
            } else {
              doClose(Status.fromThrowable(err), new Metadata());
            }
          }
        });
        writeAdapter.init(request, WireFormat.PROTOBUF, new BridgeMessageEncoder<>(methodDescriptor.getRequestMarshaller(), compressor));
      } else {
        doClose(Status.UNAVAILABLE, new Metadata());
      }
    });
  }

  private void doClose(Status status, Metadata trailers) {
    Runnable cmd = () -> {
      listener.onClose(status, trailers);
    };
    if (exec == null) {
      cmd.run();
    } else {
      exec.execute(cmd);
    }
  }

  @Override
  public void request(int numMessages) {
    readAdapter.request(numMessages);
  }

  @Override
  public void cancel(String message, Throwable cause) {
    fut.onSuccess(req -> {
      req.cancel();
    });
  }

  @Override
  public void halfClose() {
    fut.onSuccess(req -> {
      req.end();
    });
  }

  @Override
  public void sendMessage(RequestT message) {
    fut.onSuccess(v -> {
      writeAdapter.write(message);
    });
  }
}
