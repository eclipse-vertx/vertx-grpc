package io.vertx.grpc.sandbox.generator;

import io.vertx.codegen.processor.ClassModel;

import java.util.Collections;
import java.util.Map;

/**
 * @author <a href="http://slinkydeveloper.github.io">Francesco Guardiani @slinkydeveloper</a>
 */
public class EventBusClientGenerator extends io.vertx.codegen.processor.Generator<ClassModel> {

  public EventBusClientGenerator() {
    kinds = Collections.singleton("class");
  }

  @Override
  public String filename(ClassModel model) {
    return model.getIfacePackageName() + "." + model.getIfaceSimpleName() + "EventBusClient" + ".java";
  }

  @Override
  public String render(ClassModel model, int index, int size, Map<String, Object> session) {
    return "package " + model.getIfacePackageName() + ";\r\n" +
      "public abstract class " + model.getIfaceSimpleName() + "EventBusClient implements " + model.getIfaceSimpleName() + "Client {\r\n" +
      "}";
  }
}
