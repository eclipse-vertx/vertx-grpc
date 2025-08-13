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
package io.vertx.grpc.common;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A compressor for gRPC messages.
 */
@VertxGen
public interface GrpcCompressor {

  Set<GrpcCompressor> COMPRESSORS = ServiceLoader.load(GrpcCompressor.class).stream().map(ServiceLoader.Provider::get).collect(Collectors.toUnmodifiableSet());

  static Set<GrpcCompressor> getDefaultCompressors() {
    return COMPRESSORS;
  }

  static GrpcCompressor lookupCompressor(String encoding) {
    return getDefaultCompressors().stream()
      .filter(compressor -> compressor.encoding().equals(encoding))
      .findFirst()
      .orElse(null);
  }

  /**
   * @return the encoding name of this compressor (e.g., "gzip")
   */
  String encoding();

  /**
   * Compresses the given buffer.
   *
   * @param data the buffer to compress
   * @return the compressed buffer
   * @throws CodecException if compression fails
   */
  Buffer compress(Buffer data) throws CodecException;
}
