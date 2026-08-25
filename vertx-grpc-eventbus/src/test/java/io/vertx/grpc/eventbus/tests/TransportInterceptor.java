package io.vertx.grpc.eventbus.tests;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.eventbus.impl.EventBusGrpcCodec;
import io.vertx.grpc.eventbus.impl.EventBusHeaders;
import io.vertx.grpc.eventbus.transport.v1alpha.Ping;
import io.vertx.grpc.eventbus.transport.v1alpha.TransportFrame;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static io.vertx.grpc.eventbus.impl.EventBusHeaders.HEADER_PREFIX;
import static io.vertx.grpc.eventbus.impl.EventBusHeaders.TRAILER_PREFIX;

/**
 * Transport interceptor for testing purpose.
 */
public class TransportInterceptor implements Handler<DeliveryContext<Object>> {

  private static class Stream {
    String clientAddress;
    String serverAddress;
  }

  private final Map<String, Stream> streams = new ConcurrentHashMap<>();
  private final Map<String, Message<?>> pendingReplies = new ConcurrentHashMap<>();

  @Override
  public final void handle(DeliveryContext<Object> ctx) {
    Deque<Supplier<Result>> actions = new ArrayDeque<>();
    Message<?> msg = ctx.message();
    MultiMap headers = msg.headers();
    String actionHeader = headers.get(EventBusHeaders.SERVICE_PROXY_ACTION);
    String formatHeader = headers.get(EventBusHeaders.STREAM_WIRE_FORMAT);
    WireFormat wireFormat;
    if (formatHeader != null) {
      switch (formatHeader) {
        case "proto":
          wireFormat = WireFormat.PROTOBUF;
          break;
        case "json":
          wireFormat = WireFormat.JSON;
          break;
        default:
          wireFormat = null;
          break;
      }
    } else {
      wireFormat = null;
    }

    if (actionHeader != null) {
      if (msg.replyAddress() != null) {
        String address = headers.get(EventBusHeaders.ENDPOINT_ADDRESS);
        Stream stream = new Stream();
        stream.clientAddress = address;
        String streamId = headers.get(EventBusHeaders.STREAM_ID);
        streams.put(streamId, stream);
        pendingReplies.put(msg.replyAddress(), msg);
        Object body = msg.body();
        actions.add(() -> onClientConnect(address, streamId));
        MultiMap streamHeaders = MultiMap.caseInsensitiveMultiMap();
        EventBusHeaders.decodeMultimap(HEADER_PREFIX, msg.headers(), streamHeaders);
        actions.add(() -> onClientHeaders(address, streamId, streamHeaders));
        if (body != null) {
          actions.add(() -> onClientMessage(address, wireFormat, streamId, (Buffer)body));
          actions.add(() -> onClientHalfClose(address, streamId));
        }
      }
    } else {
      Message<?> pendingReply = pendingReplies.remove(msg.address());
      if (pendingReply != null) {
        String address = headers.get(EventBusHeaders.ENDPOINT_ADDRESS);
        String streamId = pendingReply.headers().get(EventBusHeaders.STREAM_ID);
        Stream stream = streams.get(streamId);
        stream.serverAddress = address;
        actions.add(() -> onServerConnect(address, streamId));
        Object body = msg.body();
        if (body != null) {
          if (body instanceof ReplyException) {
            ReplyException replyException = (ReplyException) body;
            int status = replyException.failureCode();
            String statusMessage = replyException.getMessage();
            actions.add(() -> onServerHalfClose(address, streamId, status, statusMessage, MultiMap.caseInsensitiveMultiMap()));
          } else {
            MultiMap streamHeaders = MultiMap.caseInsensitiveMultiMap();
            EventBusHeaders.decodeMultimap(HEADER_PREFIX, msg.headers(), streamHeaders);
            MultiMap streamTrailers = MultiMap.caseInsensitiveMultiMap();
            EventBusHeaders.decodeMultimap(TRAILER_PREFIX, msg.headers(), streamTrailers);
            actions.add(() -> onServerHeaders(address, streamId, streamHeaders));
            actions.add(() -> onServerMessage(address, streamId, wireFormat, (Buffer)body));
            actions.add(() -> onServerHalfClose(address, streamId, 0, null, streamTrailers));
          }
        }
      } else {
        if (wireFormat != null) {
          TransportFrame frame = EventBusGrpcCodec.decodeFrame(msg);
          String streamId = "" + frame.getStreamId();
          if (streamId.equals("0")) {
            if (frame.getFrameCase() == TransportFrame.FrameCase.PING) {
              String src = msg.headers().get(EventBusHeaders.ENDPOINT_ADDRESS);
              String dst = msg.address();
              Ping ping = frame.getPing();
              actions.add(() -> onPing(src, dst, ping.getData(), ping.getAck()));
            }
          } else {
            String address = msg.address();
            Stream stream = streams.get(streamId);
            if (stream.clientAddress.equals(address)) {
              switch (frame.getFrameCase()) {
                case HEADERS:
                  MultiMap streamHeaders = MultiMap.caseInsensitiveMultiMap();
                  EventBusHeaders.decodeMultimap(HEADER_PREFIX, msg.headers(), streamHeaders);
                  actions.add(() -> onServerHeaders(stream.serverAddress, streamId, streamHeaders));
                  break;
                case MESSAGE:
                  actions.add(() -> onServerMessage(stream.serverAddress, streamId, wireFormat, Buffer.buffer(frame.getMessage().getBytes().toByteArray())));
                  break;
                case TRAILERS:
                  MultiMap streamTrailers = MultiMap.caseInsensitiveMultiMap();
                  EventBusHeaders.decodeMultimap(TRAILER_PREFIX, msg.headers(), streamTrailers);
                  int status = frame.getTrailers().getStatus();
                  String statusMessage = frame.getTrailers().getStatusMessage();
                  actions.add(() -> onServerHalfClose(stream.serverAddress, streamId, status, statusMessage, streamTrailers));
                  break;
                case WINDOW_UPDATE:
                  actions.add(() -> onServerWindowUpdate(stream.clientAddress, streamId, frame.getWindowUpdate().getDelta()));
                  break;
              }
            } else if (stream.serverAddress.equals(address)) {
              switch (frame.getFrameCase()) {
                case MESSAGE:
                  actions.add(() -> onClientMessage(stream.clientAddress, wireFormat, streamId, Buffer.buffer(frame.getMessage().getBytes().toByteArray())));
                  break;
                case HALF_CLOSE:
                  actions.add(() -> onClientHalfClose(stream.clientAddress, streamId));
                  break;
                case WINDOW_UPDATE:
                  actions.add(() -> onClientWindowUpdate(stream.clientAddress, streamId, frame.getWindowUpdate().getDelta()));
                  break;
                case CANCEL:
                  actions.add(() -> onClientCancel(stream.clientAddress, streamId));
                  break;
              }
            }
          }
        }
      }
    }
    apply(ctx, actions);
  }

  private static void apply(DeliveryContext<?> context, Deque<Supplier<Result>> actions) {
    Supplier<Result> action = actions.poll();
    if (action == null) {
      context.next();
    } else {
      Result result = action.get();
      result.apply((res,  err) -> {
        if (err == null) {
          apply(context, actions);
        } else {
          if (err instanceof ReplyException) {
            ReplyException replyException = (ReplyException)err;
            context.fail(replyException.failureType(), replyException.failureCode(), replyException.getMessage());
          } else {
            context.fail(ReplyFailure.ERROR, 0, err.getMessage());
          }
        }
      });
    }
  }

  protected Result onClientConnect(String clientAddress, String streamId) {
    return Result.NEXT;
  }

  protected Result onClientHeaders(String clientAddress, String streamId, MultiMap headers) {
    return Result.NEXT;
  }

  protected Result onClientMessage(String clientAddress, WireFormat wireFormat, String streamId, Buffer msg) {
    return Result.NEXT;
  }

  protected Result onClientHalfClose(String clientAddress, String streamId) {
    return Result.NEXT;
  }

  protected Result onClientWindowUpdate(String serverAddress, String streamId, int increment) {
    return Result.NEXT;
  }

  protected Result onClientCancel(String serverAddress, String streamId) {
    return Result.NEXT;
  }

  protected Result onServerConnect(String serverAddress, String streamId) {
    return Result.NEXT;
  }

  protected Result onServerHeaders(String serverAddress, String streamId, MultiMap headers) {
    return Result.NEXT;
  }

  protected Result onServerMessage(String serverAddress, String streamId, WireFormat wireFormat, Buffer msg) {
    return Result.NEXT;
  }

  protected Result onServerHalfClose(String serverAddress, String streamId, int status, String statusMessage, MultiMap trailers) {
    return Result.NEXT;
  }

  protected Result onServerWindowUpdate(String serverAddress, String streamId, int increment) {
    return Result.NEXT;
  }

  protected Result onPing(String srcAddress, String dstAddress, long data, boolean ack) {
    return Result.NEXT;
  }

  public static class Result {

    void apply(Completable<?> completion) {
    }

    private static final Result NEXT = new Result() {
      @Override
      void apply(Completable<?> completion) {
        completion.succeed();
      }
    };

    private static final Result STOP = new Result() {
      @Override
      void apply(Completable<?> completion) {
      }
    };

    public static Result cont() {
      return NEXT;
    }

    public static Result stop() {
      return STOP;
    }

    public static Result of(Future<?> result) {
      return new Result() {
        @Override
        void apply(Completable<?> completion) {
          result.onComplete((v , err) -> {
            if (err == null) {
              completion.succeed();
            } else {
              completion.fail(err);
            }
          });
        }
      };
    }

    public static Result failure(Throwable cause) {
      return new Result() {
        @Override
        void apply(Completable<?> completion) {
          completion.fail(cause);
        }
      };
    }

    private Result() {
    }
  }
}
