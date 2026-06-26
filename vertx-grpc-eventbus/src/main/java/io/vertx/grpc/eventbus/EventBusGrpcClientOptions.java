package io.vertx.grpc.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.WireFormat;

@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class EventBusGrpcClientOptions {

  /**
   * The default wire format requests use unless overridden with {@code request.format(...)} = {@link WireFormat#PROTOBUF}
   */
  public static final WireFormat DEFAULT_WIRE_FORMAT = WireFormat.PROTOBUF;

  private WireFormat wireFormat;

  /**
   * Default options.
   */
  public EventBusGrpcClientOptions() {
    wireFormat = DEFAULT_WIRE_FORMAT;
  }

  /**
   * Copy constructor.
   */
  public EventBusGrpcClientOptions(EventBusGrpcClientOptions other) {
    wireFormat = other.wireFormat;
  }

  /**
   * Creates options from JSON.
   */
  public EventBusGrpcClientOptions(JsonObject json) {
    this();
    EventBusGrpcClientOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the default wire format requests use
   */
  public WireFormat getWireFormat() {
    return wireFormat;
  }

  /**
   * Set the default wire format requests use. A request can still override it with {@code request.format(...)}.
   *
   * @param wireFormat the default wire format
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusGrpcClientOptions setWireFormat(WireFormat wireFormat) {
    this.wireFormat = wireFormat;
    return this;
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    EventBusGrpcClientOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
