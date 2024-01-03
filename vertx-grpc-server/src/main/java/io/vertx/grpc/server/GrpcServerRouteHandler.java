/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.grpc.server;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Adapt a {@link GrpcServer} to a Vert.x Web {@link io.vertx.ext.web.Route} handler.
 */
public interface GrpcServerRouteHandler extends Handler<RoutingContext> {

  static GrpcServerRouteHandler create(GrpcServer server) {
    return rc -> server.handle(rc.request());
  }
}
