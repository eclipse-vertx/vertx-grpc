package io.vertx.grpc.plugin.protoc;

import io.vertx.grpc.plugin.generation.GeneratedFile;
import io.vertx.grpc.plugin.file.FileWriter;

import java.util.List;

public class ProtocFileWriter extends FileWriter {
  @Override
  public void writeFiles(List<GeneratedFile> files, String outputDirectory) {
    // No-op for protoc plugin - files are returned in response
  }
}
