package io.vertx.grpc.sandbox.generator;

import io.vertx.codegen.processor.Generator;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.stream.Stream;

/**
 * @author <a href="http://slinkydeveloper.github.io">Francesco Guardiani @slinkydeveloper</a>
 */
public class GeneratorLoader implements io.vertx.codegen.processor.GeneratorLoader {

  @Override
  public Stream<Generator<?>> loadGenerators(ProcessingEnvironment processingEnv) {
    return Stream.of(new ClientGenerator(), new EventBusClientGenerator(), new GrpcClientGenerator());
  }
}
