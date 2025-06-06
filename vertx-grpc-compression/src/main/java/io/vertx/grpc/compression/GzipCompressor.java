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
package io.vertx.grpc.compression;

import io.netty.handler.codec.compression.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.CodecException;
import io.vertx.grpc.common.GrpcCompressor;
import io.vertx.grpc.common.GrpcDecompressor;

import java.util.function.Function;

public class GzipCompressor implements GrpcCompressor, GrpcDecompressor {

  public static final Function<Buffer, Buffer> GZIP_DECODER = data -> CompressionUtils.decode(data, ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP), "Invalid GZIP input");
  public static final Function<Buffer, Buffer> GZIP_ENCODER = data -> {
    GzipOptions options = StandardCompressionOptions.gzip();
    ZlibEncoder encoder = ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP, options.compressionLevel(), options.windowBits(), options.memLevel());
    return CompressionUtils.encode(data, encoder);
  };

  @Override
  public String encoding() {
    return "gzip";
  }

  @Override
  public Buffer compress(Buffer data) throws CodecException {
    return GZIP_ENCODER.apply(data);
  }

  @Override
  public Buffer decompress(Buffer data) throws CodecException {
    return GZIP_DECODER.apply(data);
  }
}
