/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.client;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Options configuring a gRPC client.
 */
@Unstable
@DataObject
@JsonGen(publicConverter = false)
public class GrpcClientOptions {

  /**
   * The default value for automatic deadline schedule = {@code false}.
   */
  public static final boolean DEFAULT_SCHEDULE_DEADLINE_AUTOMATICALLY = false;

  /**
   * The default value of the timeout = {@code 0} (no timeout).
   */
  public static final int DEFAULT_TIMEOUT = 0;

  /**
   * The default value of the timeout unit = {@link TimeUnit#SECONDS}.
   */
  public static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;

  /**
   * The default maximum message size in bytes accepted from a server = {@code 256KB}
   */
  public static final long DEFAULT_MAX_MESSAGE_SIZE = 256 * 1024;

  /**
   * The default compression options
   */
  public static final GrpcClientCompressionOptions DEFAULT_COMPRESSION = new GrpcClientCompressionOptions();

  private boolean scheduleDeadlineAutomatically;
  private int timeout;
  private TimeUnit timeoutUnit;
  private long maxMessageSize;
  private GrpcClientCompressionOptions compression;

  /**
   * Default constructor.
   */
  public GrpcClientOptions() {
    this.scheduleDeadlineAutomatically = DEFAULT_SCHEDULE_DEADLINE_AUTOMATICALLY;
    this.timeout = DEFAULT_TIMEOUT;
    this.timeoutUnit = DEFAULT_TIMEOUT_UNIT;
    this.maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
    this.compression = DEFAULT_COMPRESSION;
  }

  /**
   * Copy constructor.
   *
   * @param other the options to copy
   */
  public GrpcClientOptions(GrpcClientOptions other) {
    this.scheduleDeadlineAutomatically = other.scheduleDeadlineAutomatically;
    this.timeout = other.timeout;
    this.timeoutUnit = other.timeoutUnit;
    this.maxMessageSize = other.maxMessageSize;
    this.compression = new GrpcClientCompressionOptions(other.compression);
  }

  /**
   * Create a client options from JSON.
   *
   * @param json the JSON
   */
  public GrpcClientOptions(JsonObject json) {
    this();
    GrpcClientOptionsConverter.fromJson(json, this);
  }

  /**
   * @return whether the client will automatically schedule a deadline when a request carrying a timeout is sent.
   */
  public boolean getScheduleDeadlineAutomatically() {
    return scheduleDeadlineAutomatically;
  }

  /**
   * <p>Set whether a deadline is automatically scheduled when a request carrying a timeout (either set explicitly or through this
   * options instance) is sent.</p>
   *
   * <ul>
   * <li>When the automatic deadline is set and a request carrying a timeout is sent, a deadline (timer) is created to cancel the request
   * when the response has not been timely received. The deadline can be obtained with {@link GrpcClientRequest#deadline()}.</li>
   * <li>When the deadline is not set and a request carrying a timeout is sent, the timeout is sent to the server and it remains the
   * responsibility of the caller to eventually cancel the request. Note: the server might cancel the request as well when its local deadline is met.</li>
   * </ul>
   *
   * @param handleDeadlineAutomatically whether to automatically set
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcClientOptions setScheduleDeadlineAutomatically(boolean handleDeadlineAutomatically) {
    this.scheduleDeadlineAutomatically = handleDeadlineAutomatically;
    return this;
  }

  /**
   * Return the default timeout set when sending gRPC requests, the initial value is {@code 0} which does not
   * send a timeout.
   *
   * @return the default timeout.
   */
  public int getTimeout() {
    return timeout;
  }

  /**
   * Set the default timeout set when sending gRPC requests, this value should be set along with {@link #setTimeoutUnit(TimeUnit)}.
   *
   * @param timeout the timeout value
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcClientOptions setTimeout(int timeout) {
    if (timeout < 0L) {
      throw new IllegalArgumentException("Timeout value must be >= 0");
    }
    this.timeout = timeout;
    return this;
  }

  /**
   * @return the unit of time of the default timeout.
   */
  public TimeUnit getTimeoutUnit() {
    return timeoutUnit;
  }

  /**
   * Set the unit of time of the default timeout value.
   *
   * @param timeoutUnit the unit of time
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcClientOptions setTimeoutUnit(TimeUnit timeoutUnit) {
    this.timeoutUnit = Objects.requireNonNull(timeoutUnit);
    return this;
  }

  /**
   * @return the maximum message size in bytes accepted by the client
   */
  public long getMaxMessageSize() {
    return maxMessageSize;
  }

  /**
   * Set the maximum message size in bytes accepted from a server, the maximum value is {@code 0xFFFFFFFF}
   * @param maxMessageSize the size
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcClientOptions setMaxMessageSize(long maxMessageSize) {
    if (maxMessageSize <= 0) {
      throw new IllegalArgumentException("Max message size must be > 0");
    }
    if (maxMessageSize > 0xFFFFFFFFL) {
      throw new IllegalArgumentException("Max message size must be <= 0xFFFFFFFF");
    }
    this.maxMessageSize = maxMessageSize;
    return this;
  }

  /**
   * @return the compression options
   */
  public GrpcClientCompressionOptions getCompression() {
    return compression;
  }

  /**
   * Set the compression options.
   *
   * @param compression the compression options
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcClientOptions setCompression(GrpcClientCompressionOptions compression) {
    this.compression = Objects.requireNonNull(compression);
    return this;
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    GrpcClientOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
