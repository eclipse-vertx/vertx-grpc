package io.vertx.grpc.plugin;

import com.google.api.AnnotationsProto;
import com.google.api.ClientProto;
import com.salesforce.jprotoc.ProtocPlugin;

import java.util.List;

public class VertxGrpcClientGenerator {
  public static void main(String[] args) {
    if (args.length == 0) {
      ProtocPlugin.generate(List.of(new VertxGrpcGeneratorImpl(true, false)), List.of(AnnotationsProto.http, ClientProto.methodSignature));
    } else {
      ProtocPlugin.debug(List.of(new VertxGrpcGeneratorImpl(true, false)), List.of(AnnotationsProto.http, ClientProto.methodSignature), args[0]);
    }
  }
}
