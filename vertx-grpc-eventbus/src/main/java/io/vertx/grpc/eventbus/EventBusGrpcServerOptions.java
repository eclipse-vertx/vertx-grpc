package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class EventBusGrpcServerOptions {

  /**
   * The default maximum number of streams multiplexed concurrently over the server's private address = {@code 1000}
   */
  public static final int DEFAULT_MAX_CONCURRENT_STREAMS = 1000;

  private int maxConcurrentStreams;

  /**
   * Default options.
   */
  public EventBusGrpcServerOptions() {
    maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
  }

  /**
   * Copy constructor.
   */
  public EventBusGrpcServerOptions(EventBusGrpcServerOptions other) {
    maxConcurrentStreams = other.maxConcurrentStreams;
  }

  /**
   * Creates options from JSON.
   */
  public EventBusGrpcServerOptions(JsonObject json) {
    this();
    EventBusGrpcServerOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the maximum number of streams the server multiplexes concurrently over its private address
   */
  public int getMaxConcurrentStreams() {
    return maxConcurrentStreams;
  }

  /**
   * Set the maximum number of streams the server multiplexes concurrently over its private address. Opening requests
   * beyond this bound are rejected with {@code RESOURCE_EXHAUSTED}, so a flood of opens cannot grow the demux map
   * without limit.
   *
   * @param maxConcurrentStreams the maximum number of concurrent streams, must be {@code > 0}
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcServerOptions setMaxConcurrentStreams(int maxConcurrentStreams) {
    if (maxConcurrentStreams <= 0) {
      throw new IllegalArgumentException("maxConcurrentStreams must be > 0");
    }
    this.maxConcurrentStreams = maxConcurrentStreams;
    return this;
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    EventBusGrpcServerOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
