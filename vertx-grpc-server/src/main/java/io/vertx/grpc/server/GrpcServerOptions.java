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
package io.vertx.grpc.server;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * Configuration for a {@link GrpcServer}.
 */
@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class GrpcServerOptions {

  /**
   * Whether the gRPC-Web protocol should be enabled, by default = {@code true}.
   */
  public static final boolean DEFAULT_GRPC_WEB_ENABLED = true;

  /**
   * Whether the server schedule deadline automatically when a request carrying a timeout is received, by default = {@code false}
   */
  public static final boolean DEFAULT_SCHEDULE_DEADLINE_AUTOMATICALLY = false;

  /**
   * Whether the server propagates a deadline, by default = {@code false}
   */
  public static final boolean DEFAULT_PROPAGATE_DEADLINE = false;

  private boolean grpcWebEnabled;
  private boolean scheduleDeadlineAutomatically;
  private boolean deadlinePropagation;

  /**
   * Default options.
   */
  public GrpcServerOptions() {
    grpcWebEnabled = DEFAULT_GRPC_WEB_ENABLED;
    scheduleDeadlineAutomatically = DEFAULT_SCHEDULE_DEADLINE_AUTOMATICALLY;
    deadlinePropagation = DEFAULT_PROPAGATE_DEADLINE;
  }

  /**
   * Copy constructor.
   */
  public GrpcServerOptions(GrpcServerOptions other) {
    grpcWebEnabled = other.grpcWebEnabled;
    scheduleDeadlineAutomatically = other.scheduleDeadlineAutomatically;
    deadlinePropagation = other.deadlinePropagation;
  }

  /**
   * Creates options from JSON.
   */
  public GrpcServerOptions(JsonObject json) {
    this();
    GrpcServerOptionsConverter.fromJson(json, this);
  }

  /**
   * @return {@code true} if the gRPC-Web protocol should be enabled, {@code false} otherwise
   */
  public boolean isGrpcWebEnabled() {
    return grpcWebEnabled;
  }

  /**
   * Whether the gRPC-Web protocol should be enabled. Defaults to {@code true}.
   *
   * @param grpcWebEnabled {@code true} if the gRPC-Web protocol should be enabled, {@code false} otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions setGrpcWebEnabled(boolean grpcWebEnabled) {
    this.grpcWebEnabled = grpcWebEnabled;
    return this;
  }

  /**
   * @return whether the server will automatically schedule a deadline when a request carrying a timeout is received.
   */
  public boolean getScheduleDeadlineAutomatically() {
    return scheduleDeadlineAutomatically;
  }

  /**
   * <p>Set whether a deadline is automatically scheduled when a request carrying a timeout is received.</p>
   * <ul>
   * <li>When a deadline is automatically scheduled and a request carrying a timeout is received, a deadline (timer)
   * will be created to cancel the request when the response has not been timely sent. The deadline can be obtained
   * with {@link GrpcServerRequest#deadline()}.</li>
   * <li>When the deadline is not set and a request carrying a timeout is received, the timeout is available with {@link GrpcServerRequest#timeout()}
   * and it is the responsibility of the service to eventually cancel the request. Note: the client might cancel the request as well when its local
   * deadline is met.</li>
   * </ul>
   *
   * @param scheduleDeadlineAutomatically whether to schedule a deadline automatically
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions setScheduleDeadlineAutomatically(boolean scheduleDeadlineAutomatically) {
    this.scheduleDeadlineAutomatically = scheduleDeadlineAutomatically;
    return this;
  }

  /**
   * @return whether the server propagate deadlines to {@code io.vertx.grpc.client.GrpcClientRequest}.
   */
  public boolean getDeadlinePropagation() {
    return deadlinePropagation;
  }

  /**
   * Set whether the server propagate deadlines to {@code io.vertx.grpc.client.GrpcClientRequest}.
   *
   * @param deadlinePropagation the propagation setting
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions setDeadlinePropagation(boolean deadlinePropagation) {
    this.deadlinePropagation = deadlinePropagation;
    return this;
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    GrpcServerOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
