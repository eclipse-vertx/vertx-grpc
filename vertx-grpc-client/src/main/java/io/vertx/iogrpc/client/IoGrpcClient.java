package io.vertx.iogrpc.client;

import io.grpc.MethodDescriptor;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.Address;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.client.impl.GrpcClientImpl;

import java.util.function.Function;

@VertxGen
public interface IoGrpcClient extends GrpcClient {

  /**
   * Create a client.
   *
   * @param vertx the vertx instance
   * @return the created client
   */
  static IoGrpcClient client(Vertx vertx) {
    return new GrpcClientImpl(vertx);
  }

  /**
   * Create a client.
   *
   * @param vertx the vertx instance
   * @return the created client
   */
  static IoGrpcClient client(Vertx vertx, GrpcClientOptions options) {
    return new GrpcClientImpl(vertx, options, new HttpClientOptions().setHttp2ClearTextUpgrade(false));
  }

  /**
   * Create a client with the specified {@code options}.
   *
   * @param vertx the vertx instance
   * @param grpcOptions the http client options
   * @param httpOptions the http client options
   * @return the created client
   */
  static IoGrpcClient client(Vertx vertx, GrpcClientOptions grpcOptions, HttpClientOptions httpOptions) {
    return new GrpcClientImpl(vertx, grpcOptions, httpOptions);
  }

  /**
   * Create a client with the specified {@code options}.
   *
   * @param vertx the vertx instance
   * @param options the http client options
   * @return the created client
   */
  static IoGrpcClient client(Vertx vertx, HttpClientOptions options) {
    return new GrpcClientImpl(vertx, new GrpcClientOptions(), options);
  }

  /**
   * Create a client wrapping an existing {@link HttpClient}.
   *
   * @param vertx the vertx instance
   * @param client the http client instance
   * @return the created client
   */
  static IoGrpcClient client(Vertx vertx, HttpClient client) {
    return new GrpcClientImpl(vertx, client);
  }

  /**
   * Connect to the remote {@code server} and create a request for given {@code method} of a hosted gRPC service.
   *
   * @param server the server hosting the service
   * @param service the service to be called
   * @return a future request
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(Address server, MethodDescriptor<Req, Resp> service);

  /**
   * Like {@link #request(Address, MethodDescriptor)} with the default remote server.
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(MethodDescriptor<Req, Resp> service);

  /**
   * Call the {@code service} gRPC service hosted by {@code server}.
   * <p>
   *   The {@code requestHandler} is called to send the request, e.g. {@code req -> req.send(item)}
   * <p>
   *   The {@code responseFunction} extracts the result, e.g. {@code resp -> resp.last()}
   * <p>
   *   It can be used in various ways:
   * <ul>
   *   <li>{@code Future<Resp> fut = client.call(..., req -> req.send(item), resp -> resp.last());}</li>
   *   <li>{@code Future<Void> fut = client.call(..., req -> req.send(stream), resp -> resp.pipeTo(anotherService));}</li>
   *   <li>{@code Future<List<Resp>> fut = client.call(..., req -> req.send(stream), resp -> resp.collecting(Collectors.toList()));}</li>
   * </ul>
   * <pre>
   *
   * @param server the server hosting the service
   * @param service the service to call
   * @param requestHandler the handler called to send the request
   * @param resultFn the function applied to extract the result.
   * @return a future of the result
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <Req, Resp, T> Future<T> call(Address server, MethodDescriptor<Req, Resp> service, Handler<GrpcClientRequest<Req, Resp>> requestHandler, Function<GrpcClientResponse<Req, Resp>, Future<T>> resultFn) {
    return request(server, service).compose(req -> {
      requestHandler.handle(req);
      return req
        .response()
        .compose(resultFn);
    });
  }

  /**
   * Like {@link #call(Address, MethodDescriptor, Handler, Function)} with the default remote server.
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <Req, Resp, T> Future<T> call(MethodDescriptor<Req, Resp> service, Handler<GrpcClientRequest<Req, Resp>> requestHandler, Function<GrpcClientResponse<Req, Resp>, Future<T>> resultFn) {
    return request(service).compose(req -> {
      requestHandler.handle(req);
      return req
        .response()
        .compose(resultFn);
    });
  }
}
