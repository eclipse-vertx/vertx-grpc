package io.vertx.grpc.plugin.generation;

/**
 * Represents an exception that occurs during the generation process. This exception is thrown when an error prevents the successful completion of a generation task, such as
 * generating files or resources.
 * <p>
 * The exception provides constructors for specifying an error message and optionally a cause, which can help diagnose the root issue that led to the failure.
 */
public class GenerationException extends RuntimeException {

  public GenerationException(String message) {
    super(message);
  }

  public GenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}
