package io.vertx.grpc.plugin.file;

import io.vertx.grpc.plugin.generation.GeneratedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * The FileWriter class provides functionality for writing a collection of generated files to a specified output directory. Each file is written to its designated relative path
 * within the output directory. The class ensures that necessary parent directories are created prior to the writing operation.
 */
public class FileWriter {
  /**
   * Writes a list of generated files to the specified output directory. Each file is created at its designated relative path within the output directory. This method ensures that
   * any necessary parent directories are created before writing the files. If any file writing operation fails, an {@link IOException} is thrown.
   *
   * @param files a list of {@link GeneratedFile} objects to be written to the output directory. Each file contains its name, content, and a relative path where it should be
   * placed.
   * @param outputDirectory the base directory where the generated files should be written. The path specified in each {@link GeneratedFile} is resolved relative to this
   * directory.
   * @throws IOException if an I/O error occurs during the creation of directories or writing of files
   */
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
