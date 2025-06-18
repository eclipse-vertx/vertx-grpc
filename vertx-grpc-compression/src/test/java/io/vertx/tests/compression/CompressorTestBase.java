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
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.common.GrpcCompressor;
import io.vertx.grpc.common.GrpcDecompressor;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.WireFormat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public abstract class CompressorTestBase {

  /**
   * @return the encoding name of the compressor being tested (e.g., "gzip", "snappy")
   */
  protected abstract String getEncodingName();

  protected boolean shouldReduceSize() {
    return true;
  }

  @Test
  public void testCompressor(TestContext should) {
    // Create a message with some content
    String original = "Hello, World!";
    Buffer originalBuffer = Buffer.buffer(original);
    GrpcMessage message = GrpcMessage.message("identity", WireFormat.PROTOBUF, originalBuffer);

    // Compress the message using the specified compressor
    GrpcCompressor compressor = GrpcCompressor.lookupCompressor(getEncodingName());
    should.assertNotNull(compressor, getEncodingName() + " compressor should be registered");

    Buffer compressed = compressor.compress(message.payload());
    GrpcMessage compressedMessage = GrpcMessage.message(getEncodingName(), message.format(), compressed);

    // Decompress the message
    GrpcDecompressor decompressor = GrpcDecompressor.lookupDecompressor(getEncodingName());
    should.assertNotNull(decompressor, getEncodingName() + " decompressor should be registered");

    Buffer decompressed = decompressor.decompress(compressedMessage.payload());
    should.assertEquals(originalBuffer, decompressed, "Decompressed message should match original");
  }

  @Test
  public void testCompressorWithLargePayload(TestContext should) {
    // Create a message with a large content
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      sb.append("This is a test message with some repetitive content. ");
    }
    String original = sb.toString();
    Buffer originalBuffer = Buffer.buffer(original);
    GrpcMessage message = GrpcMessage.message("identity", WireFormat.PROTOBUF, originalBuffer);

    // Compress the message using the specified compressor
    GrpcCompressor compressor = GrpcCompressor.lookupCompressor(getEncodingName());
    should.assertNotNull(compressor, getEncodingName() + " compressor should be registered");

    Buffer compressed = compressor.compress(message.payload());
    GrpcMessage compressedMessage = GrpcMessage.message(getEncodingName(), message.format(), compressed);

    // Verify compression ratio if the compressor should reduce size
    if (shouldReduceSize()) {
      should.assertTrue(compressed.length() < originalBuffer.length(), "Compressed message should be smaller than original");
    }

    // Decompress the message
    GrpcDecompressor decompressor = GrpcDecompressor.lookupDecompressor(getEncodingName());
    should.assertNotNull(decompressor, getEncodingName() + " decompressor should be registered");

    Buffer decompressed = decompressor.decompress(compressedMessage.payload());
    should.assertEquals(originalBuffer, decompressed, "Decompressed message should match original");
  }

  @Test
  public void testCompressorWithEmptyPayload(TestContext should) {
    // Create a message with an empty content
    String original = "";
    Buffer originalBuffer = Buffer.buffer(original);
    GrpcMessage message = GrpcMessage.message("identity", WireFormat.PROTOBUF, originalBuffer);

    // Compress the message using the specified compressor
    GrpcCompressor compressor = GrpcCompressor.lookupCompressor(getEncodingName());
    should.assertNotNull(compressor, getEncodingName() + " compressor should be registered");

    Buffer compressed = compressor.compress(message.payload());
    GrpcMessage compressedMessage = GrpcMessage.message(getEncodingName(), message.format(), compressed);

    // Decompress the message
    GrpcDecompressor decompressor = GrpcDecompressor.lookupDecompressor(getEncodingName());
    should.assertNotNull(decompressor, getEncodingName() + " decompressor should be registered");

    Buffer decompressed = decompressor.decompress(compressedMessage.payload());
    should.assertEquals(originalBuffer, decompressed, "Decompressed message should match original");
  }
}
