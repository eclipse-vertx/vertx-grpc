package io.vertx.grpc.common;

import io.vertx.core.VertxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Signals a message that does not satisfy the validation rules of its service method.
 *
 * <p>A server maps this to {@link GrpcStatus#INVALID_ARGUMENT}.
 */
public final class GrpcValidationException extends VertxException {

  /**
   * A single rule the message did not satisfy.
   *
   * <p>The three parts are the common denominator of what validation libraries report. Protovalidate
   * fills them from the field path, the rule id and the rule message. Json schema fills them from the
   * instance location, the keyword location and the error.
   */
  public static final class Violation {

    private final String field;
    private final String rule;
    private final String message;

    public Violation(String field, String rule, String message) {
      this.field = field;
      this.rule = rule;
      this.message = message;
    }

    /**
     * @return the path of the offending field, or {@code null} when the violation is not bound to a field
     */
    public String field() {
      return field;
    }

    /**
     * @return the identifier of the rule that was not satisfied, or {@code null} when the validator does not name rules
     */
    public String rule() {
      return rule;
    }

    /**
     * @return a human readable description of the violation, or {@code null}
     */
    public String message() {
      return message;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      if (field != null) {
        sb.append(field).append(": ");
      }
      sb.append(message != null ? message : rule);
      return sb.toString();
    }
  }

  private final List<Violation> violations;

  /**
   * Create an exception reporting a single opaque failure.
   *
   * @param message the status message
   */
  public GrpcValidationException(String message) {
    this(message, Collections.<Violation>emptyList());
  }

  /**
   * Create an exception reporting {@code violations}, the status message is derived from them.
   *
   * @param violations the violations
   */
  public GrpcValidationException(List<Violation> violations) {
    this(describe(violations), violations);
  }

  private GrpcValidationException(String message, List<Violation> violations) {
    super(message, true);
    this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
  }

  /**
   * @return the violations, empty when the validator does not report them individually
   */
  public List<Violation> violations() {
    return violations;
  }

  private static String describe(List<Violation> violations) {
    if (violations.isEmpty()) {
      return "Invalid message";
    }
    StringBuilder sb = new StringBuilder("Invalid message: ");
    for (int i = 0; i < violations.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(violations.get(i));
    }
    return sb.toString();
  }
}
