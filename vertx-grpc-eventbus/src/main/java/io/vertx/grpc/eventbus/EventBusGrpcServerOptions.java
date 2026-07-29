package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.grpc.common.WireFormat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@DataObject
@Unstable
public class EventBusGrpcServerOptions {

  /**
   * The default set of wire formats the server accepts = {@code [proto, json]}
   */
  public static final Set<WireFormat> DEFAULT_SUPPORTED_WIRE_FORMATS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(WireFormat.PROTOBUF, WireFormat.JSON)));

  /**
   * The default maximum ping interval = {@code 2} minutes
   */
  public static final Duration DEFAULT_MAX_PING_INTERVAL = Duration.ofMinutes(2);

  private Set<WireFormat> supportedWireFormats;
  private Duration maxPingInterval;

  /**
   * Default options.
   */
  public EventBusGrpcServerOptions() {
    supportedWireFormats = new LinkedHashSet<>(DEFAULT_SUPPORTED_WIRE_FORMATS);
    maxPingInterval = DEFAULT_MAX_PING_INTERVAL;
  }

  /**
   * Copy constructor.
   */
  public EventBusGrpcServerOptions(EventBusGrpcServerOptions other) {
    supportedWireFormats = new LinkedHashSet<>(other.supportedWireFormats);
    maxPingInterval = other.maxPingInterval;
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
   * @return the longest ping interval the server honours
   */
  public Duration getMaxPingInterval() {
    return maxPingInterval;
  }

  /**
   * Set the longest ping interval the server honours. A client advertises the interval at which it pings and the server gives its streams up once twice that has elapsed without a
   * ping, so the client alone sets the pace. This bounds how long a client can ask the server to wait: a client that advertises more than this, or advertises nothing at all, is
   * held to this interval rather than to the one it asked for.
   *
   * The check cannot be turned off, so a client that goes away without a trace never leaves its streams registered here.
   *
   * @param maxPingInterval the longest interval, must be positive
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcServerOptions setMaxPingInterval(Duration maxPingInterval) {
    if (maxPingInterval == null || maxPingInterval.isNegative() || maxPingInterval.isZero()) {
      throw new IllegalArgumentException("maxPingInterval must be positive");
    }
    this.maxPingInterval = maxPingInterval;
    return this;
  }
}
