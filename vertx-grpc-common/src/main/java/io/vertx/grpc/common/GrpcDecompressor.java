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
 * A decompressor for gRPC messages.
 */
@VertxGen
public interface GrpcDecompressor {

  Set<GrpcDecompressor> DECOMPRESSORS = ServiceLoader.load(GrpcDecompressor.class).stream().map(ServiceLoader.Provider::get).collect(Collectors.toUnmodifiableSet());

  static Set<GrpcDecompressor> getDefaultDecompressors() {
    return DECOMPRESSORS;
  }

  static Set<String> getSupportedEncodings() {
    return getDefaultDecompressors().stream().map(GrpcDecompressor::encoding).collect(Collectors.toUnmodifiableSet());
  }

  static GrpcDecompressor lookupDecompressor(String encoding) {
    return getDefaultDecompressors().stream()
      .filter(decompressor -> decompressor.encoding().equals(encoding))
      .findFirst()
      .orElse(null);
  }

  /**
   * @return the encoding name of this decompressor (e.g., "gzip")
   */
  String encoding();

  /**
   * Decompresses the given buffer.
   *
   * @param data the buffer to decompress
   * @return the decompressed buffer
   * @throws CodecException if decompression fails
   */
  Buffer decompress(Buffer data) throws CodecException;
}
