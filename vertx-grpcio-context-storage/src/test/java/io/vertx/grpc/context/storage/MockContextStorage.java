package io.vertx.grpc.context.storage;

import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.context.storage.ContextLocal;


/**
 * Register the local storage.
 */
public class MockContextStorage implements VertxServiceProvider {

  public static final ContextLocal<CopiableObject> CONTEXT_LOCAL = ContextLocal.registerLocal(CopiableObject.class,
    CopiableObject::new);

  @Override
  public void init(VertxBootstrap builder) {
  }
}
