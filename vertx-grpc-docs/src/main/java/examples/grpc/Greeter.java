package examples.grpc;

import io.vertx.core.Future;



/**
 * <p>Contract definition Greeter service.</p>
 */
public interface Greeter {

  Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request);

}
