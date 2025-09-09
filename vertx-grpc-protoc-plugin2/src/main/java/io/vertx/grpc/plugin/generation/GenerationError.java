package io.vertx.grpc.plugin.generation;

public class GenerationError {

  private final String message;
  private final Throwable cause;
  private final GenerationType type;

  public GenerationError(String message, Throwable cause, GenerationType type) {
    this.message = message;
    this.cause = cause;
    this.type = type;
  }

  public String getMessage() {
    return message;
  }

  public Throwable getCause() {
    return cause;
  }

  public GenerationType getType() {
    return type;
  }
}
