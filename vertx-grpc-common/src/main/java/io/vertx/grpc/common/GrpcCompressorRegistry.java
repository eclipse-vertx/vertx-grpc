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

import io.vertx.grpc.common.compression.GzipCompressor;
import io.vertx.grpc.common.compression.IdentityCompressor;
import io.vertx.grpc.common.compression.SnappyCompressor;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for gRPC compressors.
 */
public class GrpcCompressorRegistry {

  private static final GrpcCompressorRegistry DEFAULT_INSTANCE = new GrpcCompressorRegistry();

  /**
   * @return the default compressor registry instance
   */
  public static GrpcCompressorRegistry getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private final Map<String, GrpcCompressor> compressors = new HashMap<>();

  /**
   * Creates a new registry with the default compressors (identity, gzip, and snappy).
   */
  public GrpcCompressorRegistry() {
    register(new IdentityCompressor());
    register(new GzipCompressor());
    register(new SnappyCompressor());
  }

  /**
   * Registers a compressor.
   *
   * @param compressor the compressor to register
   * @return this registry
   */
  public GrpcCompressorRegistry register(GrpcCompressor compressor) {
    compressors.put(compressor.encoding(), compressor);
    return this;
  }

  /**
   * Looks up a compressor by encoding name.
   *
   * @param encoding the encoding name
   * @return the compressor, or null if not found
   */
  public GrpcCompressor lookupCompressor(String encoding) {
    return compressors.get(encoding);
  }

  /**
   * @return the supported compressors
   */
  public String[] getSupportedCompressors() {
    return compressors.keySet().toArray(new String[0]);
  }
}
