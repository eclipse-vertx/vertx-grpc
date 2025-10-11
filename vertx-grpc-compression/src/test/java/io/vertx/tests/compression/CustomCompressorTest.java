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
package io.vertx.tests.compression;

import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcCompressor;
import io.vertx.grpc.common.GrpcDecompressor;

public class CustomCompressorTest extends CompressorTestBase {

  /**
   * A simple custom compressor that reverses the bytes in the buffer.
   */
  public static class ReverseCompressor implements GrpcCompressor, GrpcDecompressor {
    @Override
    public String encoding() {
      return "reverse";
    }

    @Override
    public Buffer compress(Buffer data) throws CodecException {
      byte[] bytes = data.getBytes();
      byte[] reversed = new byte[bytes.length];
      for (int i = 0; i < bytes.length; i++) {
        reversed[i] = bytes[bytes.length - 1 - i];
      }
      return Buffer.buffer(reversed);
    }

    @Override
    public Buffer decompress(Buffer data) throws CodecException {
      // For this simple example, compression and decompression are the same operation
      return compress(data);
    }
  }

  @Override
  protected String getEncodingName() {
    return "reverse";
  }

  @Override
  protected boolean shouldReduceSize() {
    return false;
  }
}
