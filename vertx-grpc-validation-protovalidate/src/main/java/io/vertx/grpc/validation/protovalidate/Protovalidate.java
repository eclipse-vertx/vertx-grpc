package io.vertx.grpc.validation.protovalidate;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.ValidatorFactory;
import build.buf.protovalidate.exceptions.ValidationException;
import build.buf.validate.FieldPathElement;
import com.google.protobuf.Message;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.VertxException;
import io.vertx.grpc.common.GrpcMessageValidator;
import io.vertx.grpc.common.GrpcValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A message validator backed by <a href="https://buf.build/docs/protovalidate">protovalidate</a>.
 *
 * <p>Rules are read from the message descriptor, so one instance validates every protobuf message type
 * and binds to any service method whose request type is a {@link Message}.
 *
 * <pre>{@code
 * server.callHandler(GREET, handler, Protovalidate.create());
 * }</pre>
 */
@Unstable
@GenIgnore(GenIgnore.PERMITTED_TYPE)
public interface Protovalidate extends GrpcMessageValidator<Message> {

  /**
   * Create a validator compiling the rules of a message type the first time that type is validated.
   *
   * @return the validator
   */
  static Protovalidate create() {
    return create(ValidatorFactory.newBuilder().build());
  }

  /**
   * Create a validator backed by {@code validator}.
   *
   * <p>Use this to configure protovalidate, in particular to compile rules eagerly so that rules that
   * do not compile are reported when the server is assembled instead of on the first call that uses them:
   *
   * <pre>{@code
   * Protovalidate.create(ValidatorFactory.newBuilder().buildWithDescriptors(descriptors, true));
   * }</pre>
   *
   * @param validator the protovalidate validator
   * @return the validator
   */
  static Protovalidate create(Validator validator) {
    Objects.requireNonNull(validator, "validator is null");
    return message -> {
      ValidationResult result;
      try {
        result = validator.validate(message);
      } catch (ValidationException e) {
        throw new VertxException("Could not validate " + message.getDescriptorForType().getFullName(), e);
      }
      if (!result.isSuccess()) {
        throw new GrpcValidationException(violations(result));
      }
    };
  }

  private static List<GrpcValidationException.Violation> violations(ValidationResult result) {
    List<GrpcValidationException.Violation> violations = new ArrayList<>(result.getViolations().size());
    for (build.buf.protovalidate.Violation violation : result.getViolations()) {
      build.buf.validate.Violation proto = violation.toProto();
      String field = proto.hasField() ? proto.getField().getElementsList().stream().map(FieldPathElement::getFieldName).collect(Collectors.joining(".")) : null;
      violations.add(new GrpcValidationException.Violation(field, proto.hasRuleId() ? proto.getRuleId() : null, proto.hasMessage() ? proto.getMessage() : null));
    }
    return violations;
  }
}
