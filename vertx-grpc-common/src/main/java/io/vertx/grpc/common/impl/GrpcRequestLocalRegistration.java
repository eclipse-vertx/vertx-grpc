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
package io.vertx.grpc.common.impl;

import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.context.storage.ContextLocal;

/**
 * Registration of context local for {@link GrpcRequestLocal}.
 */
public class GrpcRequestLocalRegistration implements VertxServiceProvider {

  static final ContextLocal<GrpcRequestLocal> CONTEXT_LOCAL = ContextLocal.registerLocal(GrpcRequestLocal.class);

  @Override
  public void init(VertxBuilder builder) {
  }
}
