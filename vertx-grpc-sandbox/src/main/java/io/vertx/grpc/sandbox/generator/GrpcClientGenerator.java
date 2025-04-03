package io.vertx.grpc.sandbox.generator;

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.processor.ClassModel;
import io.vertx.codegen.processor.MethodInfo;
import io.vertx.codegen.processor.MethodKind;
import io.vertx.codegen.processor.ParamInfo;
import io.vertx.codegen.processor.type.AnnotationValueInfo;
import io.vertx.codegen.processor.type.ClassTypeInfo;
import io.vertx.codegen.processor.type.ParameterizedTypeInfo;
import io.vertx.codegen.processor.type.TypeInfo;
import io.vertx.codegen.processor.writer.CodeWriter;
import io.vertx.grpc.common.annotations.GrpcClass;
import io.vertx.grpc.common.annotations.GrpcMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author <a href="http://slinkydeveloper.github.io">Francesco Guardiani @slinkydeveloper</a>
 */
public class GrpcClientGenerator extends io.vertx.codegen.processor.Generator<ClassModel> {

  public GrpcClientGenerator() {
    kinds = Collections.singleton("class");
  }

  @Override
  public String filename(ClassModel model) {
    return model.getIfacePackageName() + "." + model.getIfaceSimpleName() + "GrpcClient" + ".java";
  }

  @Override
  public Collection<Class<? extends Annotation>> annotations() {
    return Arrays.asList(Unstable.class);
  }

  @Override
  public String render(ClassModel model, int index, int size, Map<String, Object> session) {
    StringWriter buffer = new StringWriter();
    PrintWriter writer = new PrintWriter(buffer);

    writer.println("package " + model.getIfacePackageName() + ";");
    writer.println("import io.vertx.grpc.client.GrpcClient;");
    writer.println("import io.vertx.grpc.common.ServiceName;");
    writer.println("import io.vertx.grpc.common.ServiceMethod;");
    writer.println("import io.vertx.grpc.common.GrpcMessageEncoder;");
    writer.println("import io.vertx.grpc.common.GrpcMessageDecoder;");
    writer.println("import io.vertx.core.net.SocketAddress;");
    writer.println("public abstract class " + model.getIfaceSimpleName() + "GrpcClient implements " + model.getIfaceSimpleName() + "Client {");

    writer.println("  private final GrpcClient client;");
    writer.println("  private final SocketAddress socketAddress;");

    writer.println("  public " + model.getIfaceSimpleName() + "GrpcClient(GrpcClient client, SocketAddress socketAddress) {");
    writer.println("    this.client = client;");
    writer.println("    this.socketAddress = socketAddress;");
    writer.println("  }");

    AnnotationValueInfo grpcService = model.getAnnotations()
      .stream().filter(ann -> ann.getName().equals(GrpcClass.class.getName())).findFirst().stream().findFirst().get();

    for (MethodInfo method : model.getAnyJavaTypeMethods()) {
      if (method.getKind() == MethodKind.FUTURE && method.getParams().size() == 1) {
        TypeInfo requestType = method.getParam(0).getType();
        TypeInfo responseType = ((ParameterizedTypeInfo) method.getReturnType()).getArg(0);
        boolean unaryRequest = !requestType.getName().startsWith("io.vertx.core.streams.ReadStream");
        boolean unaryResponse = !responseType.getName().startsWith("io.vertx.core.streams.ReadStream");
        writer.print("  public " + method.getReturnType() + " " + method.getName() + "(");
        String s = method.getParams().stream().map(ParamInfo::toString).collect(Collectors.joining(","));
        writer.print(s);
        writer.println(") {");
        if (unaryRequest && unaryResponse) {
          List<AnnotationValueInfo> annotationValueInfos = model.getAnyMethodAnnotations().get(method.getName());
          AnnotationValueInfo first = annotationValueInfos.stream().filter(ann -> ann.getName().equals(GrpcMethod.class.getName())).findFirst().stream().findFirst().get();
          String methodName = first.getMember("name").toString();
          writer.println("    ServiceMethod<" + responseType.getName() + ", " + requestType.getName() + "> serviceMethod = " +
            "ServiceMethod.client(");
          writer.println("      ServiceName.create(\"" + grpcService.getMember("packageName") + ", " + grpcService.getMember("name") + "\"),");
          writer.println("      \"" + methodName + "\",");
          writer.println("      GrpcMessageEncoder.encoder(),");
          writer.println("      GrpcMessageDecoder.decoder(" + responseType.getName() + ".parser())");
          writer.println("      );");
          writer.println("    return client.request(socketAddress, serviceMethod).compose(req -> {");
          writer.println("      req.end(request);");
          writer.println("      return req.response().compose(resp -> resp.last());");
          writer.println("    });");
        } else {
          writer.println("    throw new UnsupportedOperationException();");
        }
        writer.println("  }");
      }
    }

    writer.println("}");

    return buffer.toString();
  }
}
