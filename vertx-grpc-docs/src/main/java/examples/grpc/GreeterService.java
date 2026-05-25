package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Completable;



/**
 * <p>Provides support for RPC methods implementations of the Greeter gRPC service.</p>
 *
 * <p>The following methods of this class should be overridden to provide an implementation of the service:</p>
 * <ul>
 *   <li>SayHello</li>
 * </ul>
 */
public class GreeterService implements Greeter {

  /**
   * Override this method to implement the SayHello RPC.
   */
  public Future<examples.grpc.HelloReply> sayHello(examples.grpc.HelloRequest request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void sayHello(examples.grpc.HelloRequest request, Completable<examples.grpc.HelloReply> response) {
    sayHello(request).onComplete(response);
  }
}
