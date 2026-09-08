package io.vertx.grpc.common.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class GrpcTestUtils {

  /**
   * Produce a gzip bomb of {@code size}.
   */
  public static byte[] gzipBomb(int size) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
      byte[] zeros = new byte[1024];
      while (size > zeros.length) {
        gzos.write(zeros);
        size -= zeros.length;
      }
      gzos.write(zeros, 0, size);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return baos.toByteArray();
  }
}
