/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.health;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;

import java.time.Duration;

/**
 * Configuration for a {@link HealthService}.
 */
@Unstable
@DataObject
public class HealthServiceOptions {

  /**
   * The health check interval in milliseconds, by default = {@code 2500} milliseconds.
   */
  public static final Duration HEALTH_CHECK_INTERVAL = Duration.ofMillis(2500);

  private Duration healthCheckInterval;

  /**
   * Default options.
   */
  public HealthServiceOptions() {
    healthCheckInterval = HEALTH_CHECK_INTERVAL;
  }

  /**
   * Copy constructor.
   */
  public HealthServiceOptions(HealthServiceOptions other) {
    healthCheckInterval = other.healthCheckInterval;
  }

  /**
   * @return the health check interval in {@link Duration}
   */
  public Duration getHealthCheckInterval() {
    return healthCheckInterval;
  }

  /**
   * Set the health check interval in milliseconds
   *
   * @param healthCheckInterval the interval in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  public HealthServiceOptions setHealthCheckInterval(Duration healthCheckInterval) {
    if (healthCheckInterval == null || healthCheckInterval.toMillis() <= 0) {
      throw new IllegalArgumentException("Health check interval must be > 0 milliseconds. Provided: " + healthCheckInterval + " milliseconds.");
    }
    this.healthCheckInterval = healthCheckInterval;
    return this;
  }
}
