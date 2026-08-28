package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.grpc.common.WireFormat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Options for configuring an {@link EventBusGrpcServer}.
 */
@DataObject
@Unstable
public class EventBusGrpcServerOptions extends EventBusGrpcEndpointOptions {

  /**
   * The default set of wire formats the server accepts = {@code [proto, json]}
   */
  public static final Set<WireFormat> DEFAULT_ENABLED_WIRE_FORMATS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(WireFormat.PROTOBUF, WireFormat.JSON)));

  /**
   * The default maximum ping timeout = {@code 2} minutes
   */
  public static final Duration DEFAULT_MAX_PING_TIMEOUT = Duration.ofMinutes(2);

  private Set<WireFormat> enabledFormats;
  private Duration maxPingTimeout;

  /**
   * Default options.
   */
  public EventBusGrpcServerOptions() {
    enabledFormats = new LinkedHashSet<>(DEFAULT_ENABLED_WIRE_FORMATS);
    maxPingTimeout = DEFAULT_MAX_PING_TIMEOUT;
  }

  /**
   * Copy constructor.
   */
  public EventBusGrpcServerOptions(EventBusGrpcServerOptions other) {
    super(other);
    enabledFormats = new LinkedHashSet<>(other.enabledFormats);
    maxPingTimeout = other.maxPingTimeout;
  }

  /**
   * @return the set of wire formats the server accepts
   */
  public Set<WireFormat> getEnabledFormats() {
    return enabledFormats;
  }

  /**
   * Set the wire formats the server accepts for the grpc payload. A request using a wire format outside this set is rejected with
   * {@code UNIMPLEMENTED}.
   *
   * @param enabledFormats the supported wire formats, must not be empty
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcServerOptions setEnabledFormats(Set<WireFormat> enabledFormats) {
    if (enabledFormats == null || enabledFormats.isEmpty()) {
      throw new IllegalArgumentException("supportedWireFormats must not be empty");
    }
    this.enabledFormats = new LinkedHashSet<>(enabledFormats);
    return this;
  }

  /**
   * @return the longest ping timeout the server honours
   */
  public Duration getMaxPingTimeout() {
    return maxPingTimeout;
  }

  /**
   * Set the longest ping timeout the server honours. A client advertises how long it may go unheard and the server holds it to that very deadline, the same one the client applies
   * to this server, so a delay that one side rides out does not cost the stream on the other. This bounds how long a client can ask the server to wait: a client that advertises
   * more than this, or advertises nothing at all, is held to this timeout rather than to the one it asked for.
   *
   * The check cannot be turned off, so a client that goes away without a trace never leaves its streams registered here.
   *
   * @param maxPingTimeout the longest timeout, must be positive
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcServerOptions setMaxPingTimeout(Duration maxPingTimeout) {
    if (maxPingTimeout == null || maxPingTimeout.isNegative() || maxPingTimeout.isZero()) {
      throw new IllegalArgumentException("maxPingTimeout must be positive");
    }
    this.maxPingTimeout = maxPingTimeout;
    return this;
  }

  @Override
  public EventBusGrpcServerOptions setCleanerPeriod(Duration cleanerPeriod) {
    return (EventBusGrpcServerOptions)super.setCleanerPeriod(cleanerPeriod);
  }

  @Override
  public EventBusGrpcServerOptions setWireFormat(WireFormat wireFormat) {
    return (EventBusGrpcServerOptions)super.setWireFormat(wireFormat);
  }

  @Override
  public EventBusGrpcServerOptions setInitialWindowSize(int initialWindowSize) {
    return (EventBusGrpcServerOptions)super.setInitialWindowSize(initialWindowSize);
  }
}
