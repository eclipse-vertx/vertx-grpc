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
import io.vertx.grpc.common.JsonReaderConfig;
import io.vertx.grpc.common.JsonWriterConfig;
import io.vertx.grpc.common.WireFormat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for a {@link GrpcServer}.
 */
@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class GrpcServerOptions {

  /**
   * The default set of enabled protocols = {@code [HTTP/2, TRANSCODING, WEB, WEB_TEXT]}
   */
  public static final Set<GrpcProtocol> DEFAULT_ENABLED_PROTOCOLS = Collections.unmodifiableSet(EnumSet.allOf(GrpcProtocol.class));

  /**
   * The default set of enabled wire formats = {@code [PROTOBUF, JSON]}
   */
  public static final Set<WireFormat> DEFAULT_ENABLED_FORMATS = Collections.unmodifiableSet(EnumSet.of(WireFormat.PROTOBUF, WireFormat.JSON));

  /**
   * Whether the server schedule deadline automatically when a request carrying a timeout is received, by default = {@code false}
   */
  public static final boolean DEFAULT_SCHEDULE_DEADLINE_AUTOMATICALLY = false;

  /**
   * Whether the server propagates a deadline, by default = {@code false}
   */
  public static final boolean DEFAULT_PROPAGATE_DEADLINE = false;

  /**
   * The default maximum message size in bytes accepted from a client = {@code 256KB}
   */
  public static final long DEFAULT_MAX_MESSAGE_SIZE = 256 * 1024;

  public static final JsonReaderConfig DEFAULT_JSON_READER_CONFIG_CONFIG = JsonReaderConfig.DEFAULT;
  public static final JsonWriterConfig DEFAULT_JSON_WRITER_CONFIG_CONFIG = JsonWriterConfig.DEFAULT;

  private Set<GrpcProtocol> enabledProtocols;
  private Set<WireFormat> enabledFormats;
  private JsonReaderConfig jsonReaderConfig;
  private JsonWriterConfig jsonWriterConfig;
  private boolean scheduleDeadlineAutomatically;
  private boolean deadlinePropagation;
  private long maxMessageSize;

  /**
   * Default options.
   */
  public GrpcServerOptions() {
    enabledProtocols = EnumSet.copyOf(DEFAULT_ENABLED_PROTOCOLS);
    enabledFormats = EnumSet.copyOf(DEFAULT_ENABLED_FORMATS);
    jsonReaderConfig = DEFAULT_JSON_READER_CONFIG_CONFIG;
    jsonWriterConfig = DEFAULT_JSON_WRITER_CONFIG_CONFIG;
    scheduleDeadlineAutomatically = DEFAULT_SCHEDULE_DEADLINE_AUTOMATICALLY;
    deadlinePropagation = DEFAULT_PROPAGATE_DEADLINE;
    maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  }

  /**
   * Copy constructor.
   */
  public GrpcServerOptions(GrpcServerOptions other) {
    enabledProtocols = EnumSet.copyOf(other.enabledProtocols);
    enabledFormats = EnumSet.copyOf(other.enabledFormats);
    jsonReaderConfig = other.jsonReaderConfig;
    jsonWriterConfig = other.jsonWriterConfig;
    scheduleDeadlineAutomatically = other.scheduleDeadlineAutomatically;
    deadlinePropagation = other.deadlinePropagation;
    maxMessageSize = other.maxMessageSize;
  }

  /**
   * Creates options from JSON.
   */
  public GrpcServerOptions(JsonObject json) {
    this();
    GrpcServerOptionsConverter.fromJson(json, this);
  }

  /**
   * Determines if the specified gRPC protocol is enabled in the current server configuration.
   *
   * @param protocol the gRPC protocol to check
   * @return true if the protocol is enabled; false otherwise
   */
  public boolean isProtocolEnabled(GrpcProtocol protocol) {
    return enabledProtocols.contains(protocol);
  }

  /**
   * @return the wire formats the server accepts on inbound calls. The set is mutable. Entries
   *         compare by {@link WireFormat#canonicalName()}, so it holds at most one instance per format
   *         name. The configured one wins.
   */
  public Set<WireFormat> getEnabledFormats() {
    return enabledFormats;
  }

  /**
   * Adds a gRPC protocol to the list of enabled protocols for the server.
   *
   * @param protocol the gRPC protocol to enable
   * @return a reference to this GrpcServerOptions instance, allowing method chaining
   */
  public GrpcServerOptions addEnabledProtocol(GrpcProtocol protocol) {
    enabledProtocols.add(protocol);
    return this;
  }

  /**
   * Removes the specified gRPC protocol from the set of enabled protocols.
   *
   * @param protocol the gRPC protocol to be removed
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions removeEnabledProtocol(GrpcProtocol protocol) {
    enabledProtocols.remove(protocol);
    return this;
  }

  /**
   * Retrieves the set of gRPC protocols that are currently enabled for the server.
   *
   * @return a set of enabled gRPC protocols defined by {@link GrpcProtocol}
   */
  public Set<GrpcProtocol> getEnabledProtocols() {
    return enabledProtocols;
  }

  /**
   * @return the server json reader config
   */
  public JsonReaderConfig getJsonReaderConfig() {
    return jsonReaderConfig;
  }

  /**
   * Configures the json input format parsed by the server, either for {@code application/grpc+json} or {@code application/json}
   *
   * @param jsonReaderConfig the config
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions setJsonReaderConfig(JsonReaderConfig jsonReaderConfig) {
    this.jsonReaderConfig = jsonReaderConfig;
    return this;
  }

  /**
   * @return the server json writer config
   */
  public JsonWriterConfig getJsonWriterConfig() {
    return jsonWriterConfig;
  }

  /**
   * Configures the json output format emitted by the server, either for {@code application/grpc+json} or {@code application/json}
   *
   * @param jsonWriterConfig the config
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions setJsonWriterConfig(JsonWriterConfig jsonWriterConfig) {
    this.jsonWriterConfig = jsonWriterConfig;
    return this;
  }

  /**
   * Enables a wire format. Equality is name-based, so adding a format with the same name
   * replaces the previously configured instance.
   *
   * @param format the wire format to enable, must not be null
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions addEnabledFormat(WireFormat format) {
    Objects.requireNonNull(format, "format");
    enabledFormats.add(format);
    return this;
  }

  /**
   * Removes the specified wire format from the set of enabled formats.
   *
   * @param format the wire format to remove
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions removeEnabledFormat(WireFormat format) {
    enabledFormats.remove(format);
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
   * @return the maximum message size in bytes accepted by the server
   */
  public long getMaxMessageSize() {
    return maxMessageSize;
  }

  /**
   * Set the maximum message size in bytes accepted from a client, the maximum value is {@code 0xFFFFFFFF}
   * @param maxMessageSize the size
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerOptions setMaxMessageSize(long maxMessageSize) {
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
