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
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * Configuration for a {@link HealthService}.
 */
@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class HealthServiceOptions {

  /**
   * The health check interval in milliseconds, by default = {@code 2500}
   */
  public static final int HEALTH_CHECK_INTERVAL = 2500;

  private int healthCheckInterval;

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
   * Creates options from JSON.
   */
  public HealthServiceOptions(JsonObject json) {
    this();
    if (json != null) {
      if (json.containsKey("healthCheckInterval")) {
        healthCheckInterval = json.getInteger("healthCheckInterval");
      }
    }
  }

  /**
   * @return the health check interval in milliseconds
   */
  public int getHealthCheckInterval() {
    return healthCheckInterval;
  }

  /**
   * Set the health check interval in milliseconds
   * @param healthCheckInterval the interval in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  public HealthServiceOptions setHealthCheckInterval(int healthCheckInterval) {
    if (healthCheckInterval <= 0) {
      throw new IllegalArgumentException("Health check interval must be > 0");
    }
    this.healthCheckInterval = healthCheckInterval;
    return this;
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("healthCheckInterval", healthCheckInterval);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
