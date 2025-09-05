package io.vertx.grpc.client;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcCompressionOptions;

@Unstable
@DataObject
@JsonGen(publicConverter = false)
public class GrpcClientCompressionOptions extends GrpcCompressionOptions {

  /**
   * The default compression algorithm accepted by the client = {@code gzip}
   */
  public static final String DEFAULT_COMPRESSION_ALGORITHM = "identity";

  private String compressionAlgorithm;

  /**
   * Default options.
   */
  public GrpcClientCompressionOptions() {
    this.compressionAlgorithm = DEFAULT_COMPRESSION_ALGORITHM;
  }

  /**
   * Copy constructor.
   */
  public GrpcClientCompressionOptions(GrpcClientCompressionOptions other) {
    super(other);
    this.compressionAlgorithm = other.compressionAlgorithm;
  }

  /**
   * Creates options from JSON.
   */
  public GrpcClientCompressionOptions(JsonObject json) {
    this();
    GrpcClientCompressionOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the compression algorithm accepted by the client
   */
  public String getCompressionAlgorithm() {
    return compressionAlgorithm;
  }

  /**
   * Set the compression algorithm accepted by the client.
   *
   * @param compressionAlgorithm the compression algorithm
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcClientCompressionOptions setCompressionAlgorithm(String compressionAlgorithm) {
    this.compressionAlgorithm = compressionAlgorithm;
    return this;
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    GrpcClientCompressionOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
