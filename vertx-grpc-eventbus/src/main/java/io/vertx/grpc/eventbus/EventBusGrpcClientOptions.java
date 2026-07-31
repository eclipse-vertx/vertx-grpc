package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.grpc.common.WireFormat;

import java.time.Duration;

@DataObject
@Unstable
public class EventBusGrpcClientOptions {

  /**
   * The default wire format requests use unless overridden with {@code request.format(...)} = {@link WireFormat#PROTOBUF}
   */
  public static final WireFormat DEFAULT_WIRE_FORMAT = WireFormat.PROTOBUF;

  /**
   * The default ping interval = {@code 30} seconds
   */
  public static final Duration DEFAULT_PING_INTERVAL = Duration.ofSeconds(30);

  /**
   * The default ping timeout = {@code 60} seconds, twice the default ping interval
   */
  public static final Duration DEFAULT_PING_TIMEOUT = Duration.ofSeconds(60);

  private WireFormat wireFormat;
  private Duration pingInterval;
  private Duration pingTimeout;

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
   * @return the ping interval
   */
  public Duration getPingInterval() {
    return pingInterval;
  }

  /**
   * Set the interval at which the client pings each server endpoint it holds a stream with. The interval paces the probes; how long a silence has to last before a stream is given
   * up is the ping timeout.
   *
   * Pinging cannot be turned off. The event bus is not connection oriented, so it is the only thing that tells a side receiving nothing apart from a side whose peer is gone, and
   * without it such a stream would hang and leak its registration.
   *
   * @param pingInterval the interval, must be positive
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcClientOptions setPingInterval(Duration pingInterval) {
    if (pingInterval == null || pingInterval.isNegative() || pingInterval.isZero()) {
      throw new IllegalArgumentException("pingInterval must be positive");
    }
    this.pingInterval = pingInterval;
    return this;
  }

  /**
   * @return the ping timeout
   */
  public Duration getPingTimeout() {
    return pingTimeout;
  }

  /**
   * Set how long a server endpoint may go without acknowledging a ping before it is considered down and every stream with it is given up. Must be greater than the ping interval,
   * and should be a small multiple of it so an occasional late acknowledgment does not cost a live stream.
   *
   * The timeout is advertised to the server, which holds this client to the same deadline, so both sides give a stream up at the same time rather than one outliving the other.
   *
   * @param pingTimeout the timeout, must be positive
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcClientOptions setPingTimeout(Duration pingTimeout) {
    if (pingTimeout == null || pingTimeout.isNegative() || pingTimeout.isZero()) {
      throw new IllegalArgumentException("pingTimeout must be positive");
    }
    this.pingTimeout = pingTimeout;
    return this;
  }
}
