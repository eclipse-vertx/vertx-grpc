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
   * The default heartbeat interval in milliseconds = {@code 0} (disabled)
   */
  public static final long DEFAULT_HEARTBEAT_INTERVAL = 0L;

  /**
   * The default idle timeout in milliseconds = {@code 0} (disabled)
   */
  public static final long DEFAULT_IDLE_TIMEOUT = 0L;

  private WireFormat wireFormat;
  private long heartbeatInterval;
  private long idleTimeout;

  /**
   * Default options.
   */
  public EventBusGrpcClientOptions() {
    wireFormat = DEFAULT_WIRE_FORMAT;
    heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
  }

  /**
   * Copy constructor.
   */
  public EventBusGrpcClientOptions(EventBusGrpcClientOptions other) {
    wireFormat = other.wireFormat;
    heartbeatInterval = other.heartbeatInterval;
    idleTimeout = other.idleTimeout;
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
   * @return the heartbeat interval in milliseconds; {@code 0} disables heartbeats
   */
  public long getHeartbeatInterval() {
    return heartbeatInterval;
  }

  /**
   * Set the interval at which heartbeat frames are sent on streams the client produces.
   *
   * @param heartbeatInterval the interval in milliseconds, {@code 0} to disable
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcClientOptions setHeartbeatInterval(long heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
    return this;
  }

  /**
   * @return the idle timeout in milliseconds; {@code 0} disables the idle timeout
   */
  public long getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Set the maximum time the client waits for a frame on a stream it consumes before giving it up.
   *
   * @param idleTimeout the timeout in milliseconds, {@code 0} to disable
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcClientOptions setIdleTimeout(long idleTimeout) {
    this.idleTimeout = idleTimeout;
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
