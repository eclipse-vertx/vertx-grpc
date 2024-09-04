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
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;

/**
 * Grpc client options.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class GrpcClientOptions {

  /**
   * The default maximum message size in bytes accepted from a server = {@code 256KB}
   */
  public static final long DEFAULT_MAX_MESSAGE_SIZE = 256 * 1024;

  private long maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  private HttpClientOptions transportOptions = new HttpClientOptions().setHttp2ClearTextUpgrade(false);

  public GrpcClientOptions() {
  }

  public GrpcClientOptions(GrpcClientOptions options) {
    this.maxMessageSize = options.maxMessageSize;
    this.transportOptions = options.transportOptions != null ? new HttpClientOptions(options.transportOptions) : null;
  }

  public GrpcClientOptions(JsonObject json) {
    GrpcClientOptionsConverter.fromJson(json, this);
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
   * @return the HTTP transport options.
   */
  public HttpClientOptions getTransportOptions() {
    return transportOptions;
  }

  /**
   * Set the HTTP transport options.
   *
   * @param transportOptions the transpot options
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcClientOptions setTransportOptions(HttpClientOptions transportOptions) {
    this.transportOptions = transportOptions;
    return this;
  }
}
