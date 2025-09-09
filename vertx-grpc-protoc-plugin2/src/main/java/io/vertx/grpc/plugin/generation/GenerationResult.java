package io.vertx.grpc.plugin.generation;

import java.util.Collections;
import java.util.List;

public class GenerationResult {

  private final boolean success;
  private final List<GeneratedFile> files;
  private final List<GenerationError> errors;

  public GenerationResult(boolean success, List<GeneratedFile> files, List<GenerationError> errors) {
    this.success = success;
    this.files = files != null ? files : Collections.emptyList();
    this.errors = errors != null ? errors : Collections.emptyList();
  }

  public static GenerationResult success(List<GeneratedFile> files) {
    return new GenerationResult(true, files, Collections.emptyList());
  }

  public static GenerationResult failure(List<GenerationError> errors) {
    return new GenerationResult(false, Collections.emptyList(), errors);
  }

  public boolean isSuccess() {
    return success;
  }

  public List<GeneratedFile> getFiles() {
    return files;
  }

  public List<GenerationError> getErrors() {
    return errors;
  }
}
