package io.vertx.grpc.common.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.grpc.common.GrpcMessageValidator;
import io.vertx.grpc.common.GrpcReadStream;

/**
 * A {@link GrpcReadStream} that validates the messages of the stream it decorates.
 *
 * <p>A message that does not satisfy the rules is not delivered, and the failure is thrown to the decorated
 * stream, which reports it. The stream delivers no further message after a failure, since the call is over.
 *
 * <p>A raw {@link #messageHandler} is not validated, it receives messages that are not decoded yet.
 */
public class GrpcValidationStream<T> extends GrpcReadStreamDecorator<T> {

  private final GrpcMessageValidator<? super T> validator;
  private boolean failed;

  public GrpcValidationStream(GrpcReadStream<T> delegate, GrpcMessageValidator<? super T> validator) {
    super(delegate);
    this.validator = validator;
  }

  @Override
  public GrpcReadStream<T> handler(@Nullable Handler<T> handler) {
    if (handler == null) {
      return super.handler(null);
    }
    return super.handler(msg -> {
      if (failed) {
        return;
      }
      try {
        validator.validate(msg);
      } catch (RuntimeException e) {
        failed = true;
        throw e;
      }
      handler.handle(msg);
    });
  }

  @Override
  public Future<T> last() {
    return super.last().map(msg -> {
      validator.validate(msg);
      return msg;
    });
  }
}
