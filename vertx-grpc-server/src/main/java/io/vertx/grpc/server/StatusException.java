/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.server;

import io.vertx.core.VertxException;
import io.vertx.grpc.common.GrpcStatus;

/**
 * A glorified GOTO forcing a response status.
 */
public final class StatusException extends VertxException {

  /**
   * Map an exception to a GrpcStatus:
   *
   * <ul>
   *   <li>{@link StatusException} returns {@link #status()}</li>
   *   <li>{@link UnsupportedOperationException} returns {@link GrpcStatus#UNIMPLEMENTED}</li>
   *   <li>otherwise returns {@link GrpcStatus#UNKNOWN}</li>
   * </ul>
   *
   * @param t the exception to map
   * @return the mapped status
   */
  public static GrpcStatus mapStatus(Throwable t) {
    if (t instanceof StatusException) {
      return ((StatusException)t).status();
    } else if (t instanceof UnsupportedOperationException) {
      return GrpcStatus.UNIMPLEMENTED;
    } else {
      return GrpcStatus.UNKNOWN;
    }
  }

  private final GrpcStatus status;

  public StatusException(GrpcStatus status) {
    super("Grpc status " + status.name());
    this.status = status;
  }

  /**
   * @return the status
   */
  public GrpcStatus status() {
    return status;
  }
}
