/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;

/**
 * GrpcClientRequestOptions
 */
@DataObject(generateConverter = true)
public class GrpcClientRequestOptions {

  /**
   * The default request timeout = {@code 0}
   */
  public static final long DEFAULT_TIMEOUT = 0;

  private long timeout;

  /**
   * Default constructor
   */
  public GrpcClientRequestOptions() {
    timeout = DEFAULT_TIMEOUT;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public GrpcClientRequestOptions(GrpcClientRequestOptions other) {
    setTimeout(other.timeout);
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public GrpcClientRequestOptions(JsonObject json) {
    this();
    GrpcClientRequestOptionsConverter.fromJson(json, this);
  }

  /**
   * Create {@link io.vertx.core.http.RequestOptions} on socket address with grpc client options.
   *
   * @param server the grpc server to request
   * @return the {@link io.vertx.core.http.RequestOptions} with grpc client options
   */
  public RequestOptions createHttpRequestOptions(SocketAddress server) {
    RequestOptions options = new RequestOptions();

    options.setMethod(HttpMethod.POST);
    options.setServer(server);

    return options;
  }

  /**
   * @return the amount of time after which if the request does not return any data within the timeout period an
   *         {@link java.util.concurrent.TimeoutException} will be passed to the exception handler and
   *         the request will be closed.
   */
  public long getTimeout() {
    return timeout;
  }

  /**
   * Sets the amount of time after which if the request does not return any data within the timeout period an
   * {@link java.util.concurrent.TimeoutException} will be passed to the exception handler and
   * the request will be closed.
   *
   * @param timeout the amount of time in milliseconds.
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcClientRequestOptions setTimeout(long timeout) {
    this.timeout = timeout;
    return this;
  }

}
