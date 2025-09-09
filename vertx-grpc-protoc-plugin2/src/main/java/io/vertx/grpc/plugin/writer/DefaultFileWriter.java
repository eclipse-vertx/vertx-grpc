package io.vertx.grpc.plugin.writer;

import io.vertx.grpc.plugin.generation.GeneratedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * The default implementation of the {@code FileWriter} interface. This is currently unused but may be useful in the future, for CLI for example.
 */
public class DefaultFileWriter implements FileWriter {
  @Override
  public void writeFiles(List<GeneratedFile> files, String outputDirectory) throws IOException {
    for (GeneratedFile file : files) {
      Path filePath = Paths.get(outputDirectory, file.getAbsolutePath());

      // Create parent directories if they don't exist
      Files.createDirectories(filePath.getParent());

      // Write the file
      Files.write(filePath, file.getContent().getBytes(StandardCharsets.UTF_8));
    }
  }
}
