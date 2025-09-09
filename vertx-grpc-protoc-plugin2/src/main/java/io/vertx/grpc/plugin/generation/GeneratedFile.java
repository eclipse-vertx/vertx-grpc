package io.vertx.grpc.plugin.generation;

public class GeneratedFile {

  private final String fileName;
  private final String relativePath;
  private final String content;
  private final GenerationType type;

  public GeneratedFile(String fileName, String relativePath, String content, GenerationType type) {
    this.fileName = fileName;
    this.relativePath = relativePath;
    this.content = content;
    this.type = type;
  }

  public String getAbsolutePath() {
    return relativePath.isEmpty() ? fileName : relativePath + "/" + fileName;
  }

  public String getFileName() {
    return fileName;
  }

  public String getRelativePath() {
    return relativePath;
  }

  public String getContent() {
    return content;
  }

  public GenerationType getType() {
    return type;
  }
}
