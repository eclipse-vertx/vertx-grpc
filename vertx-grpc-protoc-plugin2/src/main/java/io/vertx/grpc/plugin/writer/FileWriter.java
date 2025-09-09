package io.vertx.grpc.plugin.writer;

import io.vertx.grpc.plugin.generation.GeneratedFile;

import java.io.IOException;
import java.util.List;

/**
 * The {@code FileWriter} interface defines a method for writing a collection of generated files to a specified output directory. Implementations of this interface specify the
 * behavior for persisting files, handling directory creation, and managing potential I/O errors.
 */
public interface FileWriter {
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
  void writeFiles(List<GeneratedFile> files, String outputDirectory) throws IOException;
}
