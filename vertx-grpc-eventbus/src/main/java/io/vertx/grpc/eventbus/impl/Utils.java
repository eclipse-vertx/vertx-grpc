package io.vertx.grpc.eventbus.impl;

import io.vertx.core.ThreadingModel;
import io.vertx.core.internal.ContextInternal;

class Utils {

  static ContextInternal eventLoopCtx(ContextInternal context) {
    if (context.threadingModel() == ThreadingModel.EVENT_LOOP) {
      return context;
    } else {
      return context
        .toBuilder()
        .withThreadingModel(ThreadingModel.EVENT_LOOP)
        .build();
    }
  }
}
