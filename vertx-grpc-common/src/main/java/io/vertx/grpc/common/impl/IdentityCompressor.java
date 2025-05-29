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
package io.vertx.grpc.common.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcCompressor;
import io.vertx.grpc.common.GrpcDecompressor;

public class IdentityCompressor implements GrpcCompressor, GrpcDecompressor {

  @Override
  public String encoding() {
    return "identity";
  }

  @Override
  public Buffer compress(Buffer data) throws CodecException {
    return data;
  }

  @Override
  public Buffer decompress(Buffer data) throws CodecException {
    return data;
  }
}
