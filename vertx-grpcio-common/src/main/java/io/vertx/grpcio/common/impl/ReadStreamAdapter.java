/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpcio.common.impl;

import io.vertx.grpc.common.GrpcReadStream;

/**
 * An adapter between gRPC and Vert.x back-pressure.
 */
public class ReadStreamAdapter<T> {

  private GrpcReadStream<T> stream;
  private int request = 0;

  /**
   * Init the adapter with the stream.
   */
  public final void init(GrpcReadStream<T> stream, BridgeMessageDecoder<T> decoder) {
    stream.messageHandler(msg -> {
      handleMessage(decoder.decode(msg));
    });
    stream.endHandler(v -> {
      handleClose();
    });
    stream.pause();
    int pending;
    synchronized (this) {
      this.stream = stream;
      pending = request;
      request = 0;
    }
    if (pending > 0) {
      stream.fetch(pending);
    }
  }

  /**
   * Override this to handle close event
   */
  protected void handleClose() {

  }

  /**
   * Override this to handle message event
   */
  protected void handleMessage(T msg) {

  }

  /**
   * Request {@code num} messages
   */
  public final void request(int num) {
    GrpcReadStream<T> s;
    synchronized (this) {
      s = stream;
      if (s == null) {
        request += num;
        return;
      }
    }
    s.fetch(num);
  }
}
