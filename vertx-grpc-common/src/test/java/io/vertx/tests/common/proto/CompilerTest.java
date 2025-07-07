package io.vertx.tests.common.proto;

import com.google.protobuf.Struct;
import io.vertx.grpc.common.proto.schema.MessageType;
import io.vertx.grpc.common.proto.schema.SchemaCompiler;
import org.junit.Test;

public class CompilerTest {

  @Test
  public void testCompile() {

    MessageType mt = new SchemaCompiler().compile(Struct.getDescriptor());

    System.out.println("mt = " + mt);

  }

}
