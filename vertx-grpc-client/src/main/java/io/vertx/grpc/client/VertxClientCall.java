package io.vertx.grpc.client;

import io.grpc.ClientCall;
import io.grpc.Compressor;
import io.grpc.Decompressor;
import io.grpc.DecompressorRegistry;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.vertx.core.Future;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.impl.BridgeMessageDecoder;
import io.vertx.grpc.common.impl.BridgeMessageEncoder;
import io.vertx.grpc.common.impl.ReadStreamAdapter;
import io.vertx.grpc.common.impl.Utils;
import io.vertx.grpc.common.impl.WriteStreamAdapter;

import jakarta.annotation.Nullable;
import java.util.concurrent.Executor;

class VertxClientCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {

  private final GrpcClient client;
  private final SocketAddress server;
  private final Executor exec;
  private final MethodDescriptor<RequestT, ResponseT> methodDescriptor;
  private final String encoding;
  private final Compressor compressor;
  private Future<GrpcClientRequest<RequestT, ResponseT>> fut;
  private Listener<ResponseT> listener;
  private WriteStreamAdapter<RequestT> writeAdapter;
  private ReadStreamAdapter<ResponseT> readAdapter;
  private GrpcClientRequest<RequestT, ResponseT> request;
  private GrpcClientResponse<RequestT, ResponseT> grpcResponse;

  VertxClientCall(GrpcClient client, SocketAddress server, Executor exec, MethodDescriptor<RequestT, ResponseT> methodDescriptor, String encoding, Compressor compressor) {
    this.client = client;
    this.server = server;
    this.exec = exec;
    this.methodDescriptor = methodDescriptor;
    this.encoding = encoding;
    this.compressor = compressor;
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
        Utils.writeMetadata(headers, request.headers());
        if (encoding != null) {
          request.encoding(encoding);
        }
        Future<GrpcClientResponse<RequestT, ResponseT>> responseFuture = request.response();
        responseFuture.onComplete(ar2 -> {
          if (ar2.succeeded()) {

            grpcResponse = ar2.result();

            String respEncoding = grpcResponse.encoding();
            Decompressor decompressor = DecompressorRegistry.getDefaultInstance().lookupDecompressor(respEncoding);

            BridgeMessageDecoder<ResponseT> decoder = new BridgeMessageDecoder<>(methodDescriptor.getResponseMarshaller(), decompressor);

            Metadata responseHeaders = Utils.readMetadata(grpcResponse.headers());
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
                trailers = Utils.readMetadata(grpcResponse.trailers());
              } else {
                status = Status.fromThrowable(ar.cause());
                trailers = new Metadata();
              }
              doClose(status, trailers);
            });
          } else {
            Throwable err = ar2.cause();
            if (err instanceof StreamResetException) {
              StreamResetException reset = (StreamResetException) err;
              GrpcError grpcError = GrpcError.mapHttp2ErrorCode(reset.getCode());
              if (grpcError != null) {
                doClose(Status.fromCodeValue(grpcError.status.code), new Metadata());
              } else {
                doClose(Status.UNKNOWN, new Metadata());
              }
            } else {
              doClose(Status.fromThrowable(err), new Metadata());
            }
          }
        });
        writeAdapter.init(request, new BridgeMessageEncoder<>(methodDescriptor.getRequestMarshaller(), compressor));
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
  public void cancel(@Nullable String message, @Nullable Throwable cause) {
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
