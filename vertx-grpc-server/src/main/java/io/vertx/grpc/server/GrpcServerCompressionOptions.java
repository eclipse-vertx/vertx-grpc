package io.vertx.grpc.server;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcCompressor;
import io.vertx.grpc.common.GrpcDecompressor;

import java.util.Collections;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

@Unstable
@DataObject
@JsonGen(publicConverter = false)
public class GrpcServerCompressionOptions {

  /**
   * Whether the server supports compression, by default = {@code false}
   */
  public static final boolean DEFAULT_COMPRESSION_ENABLED = false;

  /**
   * The default compression algorithms accepted by the server = {@code empty}
   */
  public static final Set<String> DEFAULT_COMPRESSION_ALGORITHMS = Collections.unmodifiableSet(GrpcDecompressor.getSupportedEncodings());

  private boolean compressionEnabled;
  private Set<String> compressionAlgorithms;

  /**
   * Default options.
   */
  public GrpcServerCompressionOptions() {
    compressionEnabled = DEFAULT_COMPRESSION_ENABLED;
    compressionAlgorithms = DEFAULT_COMPRESSION_ALGORITHMS;
  }

  /**
   * Copy constructor.
   */
  public GrpcServerCompressionOptions(GrpcServerCompressionOptions other) {
    compressionEnabled = other.compressionEnabled;
    compressionAlgorithms = other.compressionAlgorithms;
  }

  /**
   * Creates options from JSON.
   */
  public GrpcServerCompressionOptions(JsonObject json) {
    this();
    GrpcServerCompressionOptionsConverter.fromJson(json, this);
  }

  /**
   * @return whether the server supports compression
   */
  public boolean isCompressionEnabled() {
    return compressionEnabled;
  }

  /**
   * Set whether the server supports compression.
   *
   * @param compressionEnabled whether to enable compression
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerCompressionOptions setCompressionEnabled(boolean compressionEnabled) {
    this.compressionEnabled = compressionEnabled;
    return this;
  }

  /**
   * @return the compression algorithms accepted by the server
   */
  public Set<String> getCompressionAlgorithms() {
    return compressionAlgorithms;
  }

  /**
   * Set the compression algorithms accepted by the server.
   *
   * @param compressionAlgorithms the compression algorithms
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerCompressionOptions setCompressionAlgorithms(Set<String> compressionAlgorithms) {
    this.compressionAlgorithms = compressionAlgorithms;
    return this;
  }

  /**
   * Add a compression algorithm accepted by the server.
   *
   * @param compressionAlgorithm the compression algorithm
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerCompressionOptions addCompressionAlgorithm(String compressionAlgorithm) {
    this.compressionAlgorithms.add(compressionAlgorithm);
    return this;
  }

  /**
   * Remove a compression algorithm accepted by the server.
   *
   * @param compressionAlgorithm the compression algorithm
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcServerCompressionOptions removeCompressionAlgorithm(String compressionAlgorithm) {
    this.compressionAlgorithms.remove(compressionAlgorithm);
    return this;
  }

  public Map<String, GrpcCompressor> getCompressors() {
    return ServiceLoader.load(GrpcCompressor.class)
      .stream().map(ServiceLoader.Provider::get)
      .filter(compressor -> compressionAlgorithms.contains(compressor.encoding()))
      .collect(Collectors.toUnmodifiableMap(GrpcCompressor::encoding, c -> c));
  }

  public Map<String, GrpcDecompressor> getDecompressors() {
    return ServiceLoader.load(GrpcDecompressor.class)
      .stream().map(ServiceLoader.Provider::get)
      .filter(decompressor -> compressionAlgorithms.contains(decompressor.encoding()))
      .collect(Collectors.toUnmodifiableMap(GrpcDecompressor::encoding, d -> d));
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    GrpcServerCompressionOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
