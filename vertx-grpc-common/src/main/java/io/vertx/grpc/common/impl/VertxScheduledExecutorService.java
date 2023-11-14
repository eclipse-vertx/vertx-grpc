package io.vertx.grpc.common.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.impl.ContextInternal;

import java.util.List;
import java.util.concurrent.*;

public class VertxScheduledExecutorService extends AbstractExecutorService implements ScheduledExecutorService {

  private final io.vertx.core.impl.ContextInternal vertxContext;

  public VertxScheduledExecutorService(io.vertx.core.Context vertxContext) {
    this.vertxContext = (ContextInternal) vertxContext;
  }

  @Override
  public void shutdown() {

  }

  @Override
  public List<Runnable> shutdownNow() {
    return null;
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return false;
  }

  @Override
  public void execute(Runnable command) {
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    EventLoop el = vertxContext.nettyEventLoop();
    return el.schedule(() -> {
      vertxContext.dispatch(command);
    }, delay, unit);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }
}
