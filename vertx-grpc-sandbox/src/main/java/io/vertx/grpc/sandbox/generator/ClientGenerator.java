package io.vertx.grpc.sandbox.generator;

import io.vertx.codegen.processor.ClassModel;

import java.util.*;

/**
 * @author <a href="http://slinkydeveloper.github.io">Francesco Guardiani @slinkydeveloper</a>
 */
public class ClientGenerator extends io.vertx.codegen.processor.Generator<ClassModel> {

  public ClientGenerator() {
    kinds = Collections.singleton("class");
  }

  @Override
  public String filename(ClassModel model) {
    return model.getIfacePackageName() + "." + model.getIfaceSimpleName() + "Client" + ".java";
  }

  @Override
  public String render(ClassModel model, int index, int size, Map<String, Object> session) {
    return "package " + model.getIfacePackageName() + ";\r\n" +
      "public interface " + model.getIfaceSimpleName() + "Client extends " + model.getIfaceSimpleName() + " {\r\n" +
      "}";
  }
}
