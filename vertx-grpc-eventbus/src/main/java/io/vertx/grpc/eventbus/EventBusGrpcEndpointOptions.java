package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;

import java.time.Duration;

@DataObject
@Unstable
public abstract class EventBusGrpcEndpointOptions {

  public static final Duration DEFAULT_CLEANER_PERIOD = Duration.ofMillis(5);

  private Duration cleanerPeriod;

  public EventBusGrpcEndpointOptions() {
    cleanerPeriod = DEFAULT_CLEANER_PERIOD;
  }

  public Duration getCleanerPeriod() {
    return cleanerPeriod;
  }

  public EventBusGrpcEndpointOptions setCleanerPeriod(Duration cleanerPeriod) {
    if (cleanerPeriod.isNegative() || cleanerPeriod.isZero()) {
      throw new IllegalArgumentException();
    }
    this.cleanerPeriod = cleanerPeriod;
    return this;
  }
}
