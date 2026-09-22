package io.vertx.grpc.common.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcReadStream;
import io.vertx.grpc.common.InvalidMessageException;
import io.vertx.grpc.common.WireFormat;

/**
 * A {@link GrpcReadStream} that forwards everything to the stream it decorates.
 *
 * <p>Override the methods that the decoration applies to, the other methods are transparent.
 */
public abstract class GrpcReadStreamDecorator<T> implements GrpcReadStream<T> {

  protected final GrpcReadStream<T> delegate;

  protected GrpcReadStreamDecorator(GrpcReadStream<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public MultiMap headers() {
    return delegate.headers();
  }

  @Override
  public String encoding() {
    return delegate.encoding();
  }

  @Override
  public WireFormat format() {
    return delegate.format();
  }

  @Override
  public GrpcReadStream<T> handler(@Nullable Handler<T> handler) {
    delegate.handler(handler);
    return this;
  }

  @Override
  public GrpcReadStream<T> messageHandler(@Nullable Handler<GrpcMessage> handler) {
    delegate.messageHandler(handler);
    return this;
  }

  @Override
  public GrpcReadStream<T> invalidMessageHandler(@Nullable Handler<InvalidMessageException> handler) {
    delegate.invalidMessageHandler(handler);
    return this;
  }

  @Override
  public GrpcReadStream<T> errorHandler(@Nullable Handler<GrpcError> handler) {
    delegate.errorHandler(handler);
    return this;
  }

  @Override
  public GrpcReadStream<T> exceptionHandler(@Nullable Handler<Throwable> handler) {
    delegate.exceptionHandler(handler);
    return this;
  }

  @Override
  public GrpcReadStream<T> endHandler(@Nullable Handler<Void> handler) {
    delegate.endHandler(handler);
    return this;
  }

  @Override
  public GrpcReadStream<T> pause() {
    delegate.pause();
    return this;
  }

  @Override
  public GrpcReadStream<T> resume() {
    delegate.resume();
    return this;
  }

  @Override
  public GrpcReadStream<T> fetch(long amount) {
    delegate.fetch(amount);
    return this;
  }

  @Override
  public Future<T> last() {
    return delegate.last();
  }

  @Override
  public Future<Void> end() {
    return delegate.end();
  }
}
