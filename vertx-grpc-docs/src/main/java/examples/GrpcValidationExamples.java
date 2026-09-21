package examples;

import build.buf.protovalidate.ValidatorFactory;
import build.buf.protovalidate.exceptions.CompilationException;
import com.google.protobuf.Descriptors;
import examples.grpc.GreeterGrpcService;
import examples.grpc.GreeterService;
import examples.grpc.HelloReply;
import examples.grpc.HelloRequest;
import io.vertx.core.Future;
import io.vertx.docgen.Source;
import io.vertx.grpc.common.GrpcMessageValidator;
import io.vertx.grpc.common.GrpcValidationException;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.validation.protovalidate.Protovalidate;

import java.util.Collections;
import java.util.List;

@Source
public class GrpcValidationExamples {

  public void validateServiceMethod(GrpcServer server) {
    server.callHandler(GreeterGrpcService.SayHello, request -> {
      request.handler(hello -> {
        request.response().end(HelloReply.newBuilder().setMessage("Hello " + hello.getName()).build());
      });
    }, Protovalidate.create());
  }

  public void validateGeneratedService(GrpcServer server) {
    GreeterGrpcService service = new GreeterGrpcService() {
      @Override
      public Future<HelloReply> sayHello(HelloRequest request) {
        return Future.succeededFuture(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
      }
    };

    server.addService(service.validator(Protovalidate.create()));
  }

  public void validateBoundMethods(GrpcServer server, GreeterService service) {
    server.addService(GreeterGrpcService
      .builder(service)
      .bind(GreeterGrpcService.SayHello)
      .validator(Protovalidate.create())
      .build());
  }

  public void compileRulesEagerly(GrpcServer server, GreeterService service) throws CompilationException {
    List<Descriptors.Descriptor> descriptors = Collections.singletonList(
      GreeterGrpcService.SayHello.decoder().messageDescriptor());

    GrpcMessageValidator<?> validator = Protovalidate.create(ValidatorFactory
      .newBuilder()
      .buildWithDescriptors(descriptors, true));

    server.addService(GreeterGrpcService
      .builder(service)
      .bind(GreeterGrpcService.all())
      .validator(validator)
      .build());
  }

  public void customValidator(GrpcServer server) {
    GrpcMessageValidator<HelloRequest> validator = request -> {
      if (request.getName().isEmpty()) {
        throw new GrpcValidationException(Collections.singletonList(
          new GrpcValidationException.Violation("name", "required", "name must not be empty")));
      }
    };

    server.callHandler(GreeterGrpcService.SayHello, request -> {
      request.handler(hello -> {
        request.response().end(HelloReply.newBuilder().setMessage("Hello " + hello.getName()).build());
      });
    }, validator);
  }
}
