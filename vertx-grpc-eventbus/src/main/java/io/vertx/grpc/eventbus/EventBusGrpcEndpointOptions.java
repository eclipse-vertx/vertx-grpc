package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.grpc.common.WireFormat;

import java.time.Duration;

@DataObject
@Unstable
public abstract class EventBusGrpcEndpointOptions {

  public static final Duration DEFAULT_CLEANER_PERIOD = Duration.ofMillis(5);

  /**
   * The default wire format used by the endpoint to decode event-bus protobuf messages defined by {@code eventbus_transport.proto} = {@link WireFormat#PROTOBUF}
   */
  public static final WireFormat DEFAULT_WIRE_FORMAT = WireFormat.PROTOBUF;

  /**
   * The default initial window size for inbound messages = {@code 64}
   */
  public static final int DEFAULT_INITIAL_WINDOW_SIZE = 64;

  private Duration cleanerPeriod;
  private WireFormat wireFormat;
  private int initialWindowSize;

  public EventBusGrpcEndpointOptions() {
    cleanerPeriod = DEFAULT_CLEANER_PERIOD;
    wireFormat = DEFAULT_WIRE_FORMAT;
    initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
  }

  public EventBusGrpcEndpointOptions(EventBusGrpcEndpointOptions other) {
    cleanerPeriod = other.cleanerPeriod;
    wireFormat = other.wireFormat;
    initialWindowSize = other.initialWindowSize;
  }

  public Duration getCleanerPeriod() {
    return cleanerPeriod;
  }

  public EventBusGrpcEndpointOptions setCleanerPeriod(Duration cleanerPeriod) {
    if (cleanerPeriod.isNegative() || cleanerPeriod.isZero()) {
      throw new IllegalArgumentException("Cleaner period must be > 0");
    }
    this.cleanerPeriod = cleanerPeriod;
    return this;
  }

  /**
   * @return the endpoint wire format
   */
  public WireFormat getWireFormat() {
    return wireFormat;
  }

  /**
   * Set wire format used by the endpoint to decode event-bus protobuf messages defined by {@code eventbus_transport.proto}.
   *
   * @param wireFormat the endpoint wire format
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcEndpointOptions setWireFormat(WireFormat wireFormat) {
    this.wireFormat = wireFormat;
    return this;
  }

  /**
   * @return the initial window size
   */
  public int getInitialWindowSize() {
    return initialWindowSize;
  }

  /**
   * Set the initial window size.
   *
   * @param initialWindowSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcEndpointOptions setInitialWindowSize(int initialWindowSize) {
    if (initialWindowSize < 1) {
      throw new IllegalArgumentException("initialWindowSize must be > 0");
    }
    this.initialWindowSize = initialWindowSize;
    return this;
  }
}
