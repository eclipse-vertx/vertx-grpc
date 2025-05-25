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

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Registry for gRPC decompressors.
 */
public class GrpcDecompressorRegistry {

  private static final GrpcDecompressorRegistry DEFAULT_INSTANCE = new GrpcDecompressorRegistry();

  public static GrpcDecompressorRegistry getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private final Map<String, GrpcDecompressor> decompressors = new HashMap<>();

  public GrpcDecompressorRegistry() {
    ServiceLoader<GrpcDecompressor> loader = ServiceLoader.load(GrpcDecompressor.class);
    loader.stream().map(ServiceLoader.Provider::get).forEach(this::register);
  }

  /**
   * Registers a decompressor.
   *
   * @param decompressor the decompressor to register
   * @return this registry
   */
  public GrpcDecompressorRegistry register(GrpcDecompressor decompressor) {
    decompressors.put(decompressor.encoding(), decompressor);
    return this;
  }

  /**
   * Looks up a decompressor by encoding name.
   *
   * @param encoding the encoding name
   * @return the decompressor, or null if not found
   */
  public GrpcDecompressor lookupDecompressor(String encoding) {
    return decompressors.get(encoding);
  }

  /**
   * @return the supported decompressors
   */
  public String[] getSupportedDecompressors() {
    return decompressors.keySet().toArray(new String[0]);
  }
}
