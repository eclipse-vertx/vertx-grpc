package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.WireFormat;

@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class EventBusGrpcClientOptions {

  /**
   * The default wire format requests use unless overridden with {@code request.format(...)} = {@link WireFormat#PROTOBUF}
   */
  public static final WireFormat DEFAULT_WIRE_FORMAT = WireFormat.PROTOBUF;

  /**
   * The default ping interval in milliseconds = {@code 30_000} (30 seconds)
   */
  public static final long DEFAULT_PING_INTERVAL = 30_000L;

  /**
   * The default ping timeout in milliseconds = {@code 60_000} (60 seconds), twice the default ping interval
   */
  public static final long DEFAULT_PING_TIMEOUT = 60_000L;

  private WireFormat wireFormat;
  private long pingInterval;
  private long pingTimeout;

  /**
   * Default options.
   */
  public EventBusGrpcClientOptions() {
    wireFormat = DEFAULT_WIRE_FORMAT;
    pingInterval = DEFAULT_PING_INTERVAL;
    pingTimeout = DEFAULT_PING_TIMEOUT;
  }

  /**
   * Copy constructor.
   */
  public EventBusGrpcClientOptions(EventBusGrpcClientOptions other) {
    wireFormat = other.wireFormat;
    pingInterval = other.pingInterval;
    pingTimeout = other.pingTimeout;
  }

  /**
   * Creates options from JSON.
   */
  public EventBusGrpcClientOptions(JsonObject json) {
    this();
    EventBusGrpcClientOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the default wire format requests use
   */
  public WireFormat getWireFormat() {
    return wireFormat;
  }

  /**
   * Set the default wire format requests use. A request can still override it with {@code request.format(...)}.
   *
   * @param wireFormat the default wire format
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcClientOptions setWireFormat(WireFormat wireFormat) {
    this.wireFormat = wireFormat;
    return this;
  }

  /**
   * @return the ping interval in milliseconds
   */
  public long getPingInterval() {
    return pingInterval;
  }

  /**
   * Set the interval at which the client pings each server endpoint it holds a stream with. The interval is advertised to the server, which derives from it how long this client
   * may go unheard before its streams are given up, so it needs no matching setting of its own.
   *
   * Pinging cannot be turned off. The event bus is not connection oriented, so it is the only thing that tells a side receiving nothing apart from a side whose peer is gone, and
   * without it such a stream would hang and leak its registration.
   *
   * @param pingInterval the interval in milliseconds, must be greater than {@code 0}
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcClientOptions setPingInterval(long pingInterval) {
    if (pingInterval <= 0) {
      throw new IllegalArgumentException("pingInterval must be greater than 0");
    }
    this.pingInterval = pingInterval;
    return this;
  }

  /**
   * @return the ping timeout in milliseconds
   */
  public long getPingTimeout() {
    return pingTimeout;
  }

  /**
   * Set how long a server endpoint may go without acknowledging a ping before it is considered down and every stream with it is given up. Must be greater than the ping interval,
   * and should be a small multiple of it so an occasional late acknowledgment does not cost a live stream.
   *
   * @param pingTimeout the timeout in milliseconds, must be greater than {@code 0}
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcClientOptions setPingTimeout(long pingTimeout) {
    if (pingTimeout <= 0) {
      throw new IllegalArgumentException("pingTimeout must be greater than 0");
    }
    this.pingTimeout = pingTimeout;
    return this;
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    EventBusGrpcClientOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
