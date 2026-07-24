package io.vertx.grpc.eventbus.impl;

/**
 * The {@code MessageWrite} interface represents an abstraction for writing messages in a message exchange or streaming context. It provides methods to perform the message write
 * operation, handle failure scenarios, and determine if a windowed flow-control mechanism is utilized.
 *
 * Implementing classes provide the specific behavior for message writing and error handling based on the context they are used in.
 */
interface MessageWrite {

  /**
   * Indicates whether the message exchange uses a windowed flow-control mechanism.
   *
   * @return {@code true} if the message exchange utilizes windowed flow control; {@code false} otherwise.
   */
  default boolean windowed() {
    return false;
  }

  /**
   * Writes data to an appropriate destination. The specific implementation of how the data is written is determined by the implementing class.
   *
   * This method is typically invoked to perform an output operation as part of a process or pipeline which handles streaming or message exchange. The behavior may depend on the
   * configuration or state of the containing class or its dependencies.
   *
   * Implementing classes should ensure the write logic aligns with the expected operation of the system components where this method is employed.
   */
  void write();

  /**
   * Handles a failure scenario by reporting the provided cause.
   *
   * @param cause The Throwable representing the reason for the failure.
   */
  default void fail(Throwable cause) {
  }
}
