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
   * Whether the gRPC-Web protocol should be enabled, by default = true.
   */
  public static final boolean DEFAULT_GRPC_WEB_ENABLED = true;

  private boolean grpcWebEnabled;

  /**
   * Default options.
   */
  public GrpcServerOptions() {
    grpcWebEnabled = DEFAULT_GRPC_WEB_ENABLED;
  }

  /**
   * Copy constructor.
   */
  public GrpcServerOptions(GrpcServerOptions other) {
    grpcWebEnabled = other.grpcWebEnabled;
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
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    GrpcServerOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return "GrpcServerOptions{" +
           "grpcWebEnabled=" + grpcWebEnabled +
           '}';
  }
}
