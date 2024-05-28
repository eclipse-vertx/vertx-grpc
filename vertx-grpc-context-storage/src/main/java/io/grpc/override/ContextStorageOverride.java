package io.grpc.override;

import io.grpc.Context;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.grpc.contextstorage.GrpcStorage;
import io.vertx.grpc.contextstorage.ContextStorageService;

import java.util.function.Supplier;

/**
 * A {@link io.grpc.Context.Storage} implementation that uses Vert.x local context data maps when running on a duplicated context.
 * Otherwise, it falls back to thread-local storage.
 */
public class ContextStorageOverride extends Context.Storage {

  private static final ThreadLocal<Context> fallback = new ThreadLocal<>();

  public ContextStorageOverride() {
    // Do not remove, empty constructor required by gRPC
  }

  private static ContextInternal duplicate(ContextInternal context) {
    ContextInternal dup = context.duplicate();
    if (context.isDuplicate()) {
      dup.localContextData().putAll(context.localContextData()); // For now hand rolled  but should be handled by context duplication
    }
    return dup;
  }

  @Override
  public Context doAttach(Context toAttach) {
    ContextInternal vertxContext = vertxContext();
    Context toRestoreLater;
    if (vertxContext != null) {
      ContextInternal next = duplicate(vertxContext);
      ContextInternal prev = next.beginDispatch();
      next.putLocal(ContextStorageService.CONTEXT_LOCAL, SAME_THREAD, new GrpcStorage(toAttach, prev));
      GrpcStorage local = next.getLocal(ContextStorageService.CONTEXT_LOCAL, SAME_THREAD);
      toRestoreLater = local != null ? local.currentGrpcContext : null;
    } else {
      toRestoreLater = fallback.get();
      fallback.set(toAttach);
    }
    return toRestoreLater;
  }

  @Override
  public void detach(Context toDetach, Context toRestore) {
    ContextInternal vertxContext = vertxContext();
    if (vertxContext != null) {
      GrpcStorage local = vertxContext.getLocal(ContextStorageService.CONTEXT_LOCAL, SAME_THREAD);
      vertxContext.endDispatch(local.prevVertxContext);
    } else {
      if (toRestore == Context.ROOT) {
        fallback.remove();
      } else {
        fallback.set(toRestore);
      }
    }
  }

  @Override
  public Context current() {
    ContextInternal vertxContext = vertxContext();
    if (vertxContext != null) {
      GrpcStorage local = vertxContext.getLocal(ContextStorageService.CONTEXT_LOCAL);
      return local != null ? local.currentGrpcContext : null;
    } else {
      return fallback.get();
    }
  }

  private static ContextInternal vertxContext() {
    return (ContextInternal) Vertx.currentContext();
  }

  //
  private static final AccessMode SAME_THREAD = new AccessMode() {
    @Override
    public Object get(Object[] locals, int idx) {
      return locals[idx];
    }
    @Override
    public void put(Object[] locals, int idx, Object value) {
      locals[idx] = value;
    }

    @Override
    public Object getOrCreate(Object[] locals, int idx, Supplier<Object> initialValueSupplier) {
      Object value = locals[idx];
      if (value == null) {
        value = initialValueSupplier.get();
        locals[idx] = value;
      }
      return value;
    }
  };
}
