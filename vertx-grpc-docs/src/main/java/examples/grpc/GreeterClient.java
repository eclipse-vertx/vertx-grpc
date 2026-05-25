package examples.grpc;

import io.vertx.core.Future;

/**
 * <p>A client for invoking the Greeter gRPC service.</p>
 */
public interface GreeterClient extends Greeter {

  /**
   * Calls the SayHello RPC service method.
   *
   * @param request the examples.grpc.HelloRequest request message
   * @return a future of the examples.grpc.HelloReply response message
   */
  Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request);
}
