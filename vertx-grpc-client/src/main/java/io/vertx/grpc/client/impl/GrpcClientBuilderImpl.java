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

import java.util.List;

/**
 * Implementation of {@link GrpcClientBuilder}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcClientBuilderImpl<C extends GrpcClient> implements GrpcClientBuilder<C> {

  private final Vertx vertx;
  private AddressResolver addressResolver;
  private LoadBalancer loadBalancer;
  private GrpcClientOptions options;
  private HttpClientOptions httpTransportOptions;
  private HttpClientConfig httpTransportConfig;
  private ClientSSLOptions sslTransportOptions;

  public GrpcClientBuilderImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public GrpcClientBuilderImpl<C> with(GrpcClientOptions options) {
    this.options = options == null ? null : new GrpcClientOptions(options);
    return this;
  }

  @Override
  public GrpcClientBuilderImpl<C> with(HttpClientOptions transportOptions) {
    if (transportOptions != null) {
      transportOptions = new HttpClientOptions(transportOptions)
        .setProtocolVersion(HttpVersion.HTTP_2)
        .setHttp2ClearTextUpgrade(false);
    }
    this.httpTransportOptions = transportOptions;
    this.httpTransportConfig = null;
    return this;
  }

  @Override
  public GrpcClientBuilderImpl<C> withAddressResolver(AddressResolver resolver) {
    this.addressResolver = resolver;
    return this;
  }

  @Override
  public GrpcClientBuilderImpl<C> withLoadBalancer(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
    return this;
  }

  @Override
  public GrpcClientBuilder<C> with(HttpClientConfig transportConfig) {
    if (transportConfig != null) {
      transportConfig = new HttpClientConfig(transportConfig)
        .setVersions(HttpVersion.HTTP_2);
      if (transportConfig.getHttp2Config() == null) {
        transportConfig.setHttp2Config(new Http2ClientConfig());
      }
      transportConfig.getHttp2Config().setClearTextUpgrade(false);
    }
    this.httpTransportConfig = transportConfig;
    this.httpTransportOptions = null;
    return this;
  }

  @Override
  public GrpcClientBuilder<C> with(ClientSSLOptions sslOptions) {
    if (sslOptions != null) {
      sslOptions = sslOptions.copy();
    }
    this.sslTransportOptions = sslOptions;
    return this;
  }

  @Override
  public C build() {
    HttpClientBuilder builder = vertx.httpClientBuilder();
    builder.withAddressResolver(addressResolver);
    builder.withLoadBalancer(loadBalancer);
    if (httpTransportOptions != null) {
      builder.with(httpTransportOptions);
    } else {
      HttpClientConfig config = httpTransportConfig;
      if (config == null) {
        config = new HttpClientConfig()
          .setSsl(sslTransportOptions != null)
          .setVersions(HttpVersion.HTTP_2)
          .setHttp2Config(new Http2ClientConfig()
            .setClearTextUpgrade(false));
      }
      if (sslTransportOptions != null) {
        builder.with(sslTransportOptions);
      }
      builder.with(config);
    }
    GrpcClientOptions options = this.options;
    if (options == null) {
      options = new GrpcClientOptions();
    }
    return create(vertx, options, builder.build());
  }

  protected C create(Vertx vertx, GrpcClientOptions options, HttpClient transport) {
    GrpcClient client = new GrpcClientImpl(vertx, options, transport, true);
    return (C) client;
  }
}
