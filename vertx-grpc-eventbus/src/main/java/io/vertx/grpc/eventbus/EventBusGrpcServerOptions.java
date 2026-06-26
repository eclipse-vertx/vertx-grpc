package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.WireFormat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class EventBusGrpcServerOptions {

  /**
   * The default maximum number of streams multiplexed concurrently over the server's private address = {@code 1000}
   */
  public static final int DEFAULT_MAX_CONCURRENT_STREAMS = 1000;

  /**
   * The default set of wire formats the server accepts = {@code [PROTOBUF, JSON]}
   */
  public static final Set<WireFormat> DEFAULT_SUPPORTED_WIRE_FORMATS = Collections.unmodifiableSet(EnumSet.allOf(WireFormat.class));

  private int maxConcurrentStreams;
  private Set<WireFormat> supportedWireFormats;

  /**
   * Default options.
   */
  public EventBusGrpcServerOptions() {
    maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    supportedWireFormats = EnumSet.copyOf(DEFAULT_SUPPORTED_WIRE_FORMATS);
  }

  /**
   * Copy constructor.
   */
  public EventBusGrpcServerOptions(EventBusGrpcServerOptions other) {
    maxConcurrentStreams = other.maxConcurrentStreams;
    supportedWireFormats = EnumSet.copyOf(other.supportedWireFormats);
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
   * @return the set of wire formats the server accepts
   */
  public Set<WireFormat> getSupportedWireFormats() {
    return supportedWireFormats;
  }

  /**
   * @param wireFormat the wire format to test
   * @return whether the server accepts the given wire format
   */
  public boolean isWireFormatSupported(WireFormat wireFormat) {
    return supportedWireFormats.contains(wireFormat);
  }

  /**
   * Set the wire formats the server accepts. A request using a wire format outside this set is rejected with
   * {@code UNIMPLEMENTED}.
   *
   * @param supportedWireFormats the supported wire formats, must not be empty
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcServerOptions setSupportedWireFormats(Set<WireFormat> supportedWireFormats) {
    if (supportedWireFormats == null || supportedWireFormats.isEmpty()) {
      throw new IllegalArgumentException("supportedWireFormats must not be empty");
    }
    this.supportedWireFormats = EnumSet.copyOf(supportedWireFormats);
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
