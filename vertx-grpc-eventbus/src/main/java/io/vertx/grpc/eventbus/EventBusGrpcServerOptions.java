package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.WireFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class EventBusGrpcServerOptions {

  /**
   * The default set of wire formats the server accepts = {@code [proto, json]}
   */
  public static final Set<WireFormat> DEFAULT_SUPPORTED_WIRE_FORMATS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(WireFormat.PROTOBUF, WireFormat.JSON)));

  /**
   * The default heartbeat interval in milliseconds = {@code 0} (disabled)
   */
  public static final long DEFAULT_HEARTBEAT_INTERVAL = 0L;

  /**
   * The default idle timeout in milliseconds = {@code 0} (disabled)
   */
  public static final long DEFAULT_IDLE_TIMEOUT = 0L;

  private Set<WireFormat> supportedWireFormats;
  private long heartbeatInterval;
  private long idleTimeout;

  /**
   * Default options.
   */
  public EventBusGrpcServerOptions() {
    supportedWireFormats = new LinkedHashSet<>(DEFAULT_SUPPORTED_WIRE_FORMATS);
    heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
  }

  /**
   * Copy constructor.
   */
  public EventBusGrpcServerOptions(EventBusGrpcServerOptions other) {
    supportedWireFormats = new LinkedHashSet<>(other.supportedWireFormats);
    heartbeatInterval = other.heartbeatInterval;
    idleTimeout = other.idleTimeout;
  }

  /**
   * Creates options from JSON.
   */
  public EventBusGrpcServerOptions(JsonObject json) {
    this();
    EventBusGrpcServerOptionsConverter.fromJson(json, this);
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
    this.supportedWireFormats = new LinkedHashSet<>(supportedWireFormats);
    return this;
  }

  /**
   * @return the heartbeat interval in milliseconds; {@code 0} disables heartbeats
   */
  public long getHeartbeatInterval() {
    return heartbeatInterval;
  }

  /**
   * Set the interval at which heartbeat frames are sent on streams the server produces.
   *
   * @param heartbeatInterval the interval in milliseconds, {@code 0} to disable
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcServerOptions setHeartbeatInterval(long heartbeatInterval) {
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
   * Set the maximum time the server waits for a frame on a stream it consumes before giving it up.
   *
   * @param idleTimeout the timeout in milliseconds, {@code 0} to disable
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcServerOptions setIdleTimeout(long idleTimeout) {
    this.idleTimeout = idleTimeout;
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
