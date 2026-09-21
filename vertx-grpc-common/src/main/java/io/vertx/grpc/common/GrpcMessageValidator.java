package io.vertx.grpc.common;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;

/**
 * Validates decoded messages of a service method.
 *
 * <p>A validator is invoked right after a message is decoded, for every message of a stream, whichever
 * way the application consumes it. It is invoked on the event loop and must not block, so that validation
 * is subject to the same back pressure as decoding.
 *
 * <p>Validation rules come from the library backing the implementation, not from this interface. An
 * implementation over a message type typically declares itself on the supertype, for instance a protobuf
 * validator is a {@code GrpcMessageValidator<Message>} and binds to any method whose request type is a
 * protobuf message.
 */
@Unstable
@GenIgnore(GenIgnore.PERMITTED_TYPE)
@FunctionalInterface
public interface GrpcMessageValidator<T> {

  /**
   * Validate a decoded message.
   *
   * @param message the message to validate
   * @throws GrpcValidationException when the message does not satisfy the rules
   */
  void validate(T message) throws GrpcValidationException;

}
