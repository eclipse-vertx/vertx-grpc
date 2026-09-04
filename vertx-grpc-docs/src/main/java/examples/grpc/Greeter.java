package examples.grpc;

/**
 * <p>Contract definition Greeter service.</p>
 */
public interface Greeter {

  io.vertx.core.Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request);

}
