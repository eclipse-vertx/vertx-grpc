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
package io.vertx.grpc.client.impl;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientBuilder;
import io.vertx.grpc.client.GrpcClientOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link GrpcClientBuilder}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcClientBuilderImpl<C extends GrpcClient> implements GrpcClientBuilder<C> {

  private final Vertx vertx;
  private final HttpClientBuilder httpClientBuilder;
  private GrpcClientOptions options;

  public GrpcClientBuilderImpl(Vertx vertx) {

    HttpClientConfig defaultCfg = new HttpClientConfig()
      .setVersions(HttpVersion.HTTP_2)
      .setHttp2Config(new Http2ClientConfig().setClearTextUpgrade(false));

    this.vertx = vertx;
    this.httpClientBuilder = vertx.httpClientBuilder().with(defaultCfg);
  }

  @Override
  public GrpcClientBuilderImpl<C> with(GrpcClientOptions options) {
    this.options = options == null ? null : new GrpcClientOptions(options);
    return this;
  }

  @Override
  public GrpcClientBuilderImpl<C> with(HttpClientOptions transportOptions) {
    if (transportOptions != null) {
      transportOptions.setProtocolVersion(HttpVersion.HTTP_2);
    }
    httpClientBuilder.with(transportOptions);
    return this;
  }

  @Override
  public GrpcClientBuilderImpl<C> withAddressResolver(AddressResolver resolver) {
    httpClientBuilder.withAddressResolver(resolver);
    return this;
  }

  @Override
  public GrpcClientBuilderImpl<C> withLoadBalancer(LoadBalancer loadBalancer) {
    httpClientBuilder.withLoadBalancer(loadBalancer);
    return this;
  }

  @Override
  public GrpcClientBuilder<C> with(HttpClientConfig transportConfig) {
    List<HttpVersion> versions;
    if (transportConfig != null && !(versions = transportConfig.getVersions()).contains(HttpVersion.HTTP_2)) {
      versions = new ArrayList<>(versions);
      versions.add(0, HttpVersion.HTTP_2);
      transportConfig.setVersions(HttpVersion.HTTP_2);
    }
    httpClientBuilder.with(transportConfig);
    return this;
  }

  @Override
  public GrpcClientBuilder<C> with(ClientSSLOptions sslOptions) {
    httpClientBuilder.with(sslOptions);
    return this;
  }

  @Override
  public C build() {
    GrpcClientOptions options = this.options;
    if (options == null) {
      options = new GrpcClientOptions();
    }
    return create(vertx, options, httpClientBuilder.build());
  }

  protected C create(Vertx vertx, GrpcClientOptions options, HttpClient transport) {
    GrpcClient client = new GrpcClientImpl(vertx, options, transport, true);
    return (C) client;
  }
}
