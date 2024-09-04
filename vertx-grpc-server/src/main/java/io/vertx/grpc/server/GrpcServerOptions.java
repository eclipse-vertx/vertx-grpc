package io.vertx.grpc.server;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * Grpc server options.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class GrpcServerOptions {

  /**
   * The default maximum message size in bytes accepted from a client = {@code 256KB}
   */
  public static final long DEFAULT_MAX_MESSAGE_SIZE = 256 * 1024;

  private long maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;

  public GrpcServerOptions() {
  }

  public GrpcServerOptions(GrpcServerOptions options) {
    this.maxMessageSize = options.maxMessageSize;
  }

  public GrpcServerOptions(JsonObject json) {
    GrpcServerOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the maximum message size in bytes accepted by the server
   */
  public long getMaxMessageSize() {
    return maxMessageSize;
  }

  /**
   * Set the maximum message size in bytes accepted from a client, the maximum value is {@code 0xFFFFFFFF}
   * @param maxMessageSize the size
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions setMaxMessageSize(long maxMessageSize) {
    if (maxMessageSize <= 0) {
      throw new IllegalArgumentException("Max message size must be > 0");
    }
    if (maxMessageSize > 0xFFFFFFFFL) {
      throw new IllegalArgumentException("Max message size must be <= 0xFFFFFFFF");
    }
    this.maxMessageSize = maxMessageSize;
    return this;
  }
}
