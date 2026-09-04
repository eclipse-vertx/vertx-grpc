package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Completable;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * <p>A client for invoking the Greeter gRPC service.</p>
 */
public interface GreeterClient extends examples.grpc.Greeter {

  /**
   * Calls the SayHello RPC service method.
   *
   * @param request the examples.grpc.HelloRequest request message
   * @return a future of the examples.grpc.HelloReply response message
   */
  Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request);
}
