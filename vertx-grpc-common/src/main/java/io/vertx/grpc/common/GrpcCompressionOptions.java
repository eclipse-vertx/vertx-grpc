package io.vertx.grpc.common;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

@Unstable
@DataObject
@JsonGen(publicConverter = false)
public class GrpcCompressionOptions {

  private static final Set<GrpcCompressor> DEFAULT_COMPRESSORS = ServiceLoader.load(GrpcCompressor.class).stream().map(ServiceLoader.Provider::get)
    .collect(Collectors.toUnmodifiableSet());
  private static final Set<GrpcDecompressor> DEFAULT_DECOMPRESSORS = ServiceLoader.load(GrpcDecompressor.class).stream().map(ServiceLoader.Provider::get)
    .collect(Collectors.toUnmodifiableSet());

  /**
   * Whether compression is enabled, by default = {@code false}
   */
  public static final boolean DEFAULT_COMPRESSION_ENABLED = false;

  /**
   * The default compression algorithms = {@code empty}
   */
  public static final Set<String> DEFAULT_COMPRESSION_ALGORITHMS = DEFAULT_COMPRESSORS.stream().map(GrpcCompressor::encoding).collect(Collectors.toUnmodifiableSet());

  /**
   * The default decompression algorithms
   */
  public static final Set<String> DEFAULT_DECOMPRESSION_ALGORITHMS = DEFAULT_DECOMPRESSORS.stream().map(GrpcDecompressor::encoding).collect(Collectors.toUnmodifiableSet());

  private boolean compressionEnabled;
  private Set<String> compressionAlgorithms;
  private Set<String> decompressionAlgorithms;

  /**
   * Default options.
   */
  public GrpcCompressionOptions() {
    compressionEnabled = DEFAULT_COMPRESSION_ENABLED;
    compressionAlgorithms = DEFAULT_COMPRESSION_ALGORITHMS;
    decompressionAlgorithms = DEFAULT_DECOMPRESSION_ALGORITHMS;
  }

  /**
   * Copy constructor.
   */
  public GrpcCompressionOptions(GrpcCompressionOptions other) {
    compressionEnabled = other.compressionEnabled;
    compressionAlgorithms = other.compressionAlgorithms;
    decompressionAlgorithms = other.decompressionAlgorithms;
  }

  /**
   * Creates options from JSON.
   */
  public GrpcCompressionOptions(JsonObject json) {
    this();
    GrpcCompressionOptionsConverter.fromJson(json, this);
  }

  /**
   * @return whether compression is enabled
   */
  public boolean isCompressionEnabled() {
    return compressionEnabled;
  }

  /**
   * Set whether compression is enabled.
   *
   * @param compressionEnabled whether to enable compression
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcCompressionOptions setCompressionEnabled(boolean compressionEnabled) {
    this.compressionEnabled = compressionEnabled;
    return this;
  }

  /**
   * @return the supported compression algorithms
   */
  public Set<String> getCompressionAlgorithms() {
    return compressionAlgorithms;
  }

  /**
   * Set the supported compression algorithms.
   *
   * @param compressionAlgorithms the compression algorithms
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcCompressionOptions setCompressionAlgorithms(Set<String> compressionAlgorithms) {
    this.compressionAlgorithms = compressionAlgorithms;
    return this;
  }

  /**
   * Add a supported compression algorithm.
   *
   * @param compressionAlgorithm the compression algorithm
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcCompressionOptions addCompressionAlgorithm(String compressionAlgorithm) {
    this.compressionAlgorithms.add(compressionAlgorithm);
    return this;
  }

  /**
   * Remove a supported compression algorithm.
   *
   * @param compressionAlgorithm the compression algorithm
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcCompressionOptions removeCompressionAlgorithm(String compressionAlgorithm) {
    this.compressionAlgorithms.remove(compressionAlgorithm);
    return this;
  }

  /**
   * @return the supported decompression algorithms
   */
  public Set<String> getDecompressionAlgorithms() {
    return decompressionAlgorithms;
  }

  /**
   * Set the supported decompression algorithms.
   *
   * @param decompressionAlgorithms the decompression algorithms
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcCompressionOptions setDecompressionAlgorithms(Set<String> decompressionAlgorithms) {
    this.decompressionAlgorithms = decompressionAlgorithms;
    return this;
  }

  /**
   * Add a supported decompression algorithm.
   *
   * @param decompressionAlgorithm the decompression algorithm
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcCompressionOptions addDecompressionAlgorithm(String decompressionAlgorithm) {
    this.decompressionAlgorithms.add(decompressionAlgorithm);
    return this;
  }

  /**
   * Remove a supported decompression algorithm.
   *
   * @param decompressionAlgorithm the decompression algorithm
   * @return a reference to this, so the API can be used fluently
   */
  public GrpcCompressionOptions removeDecompressionAlgorithm(String decompressionAlgorithm) {
    this.decompressionAlgorithms.remove(decompressionAlgorithm);
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
      .filter(decompressor -> decompressionAlgorithms.contains(decompressor.encoding()))
      .collect(Collectors.toUnmodifiableMap(GrpcDecompressor::encoding, d -> d));
  }

  /**
   * @return a JSON representation of options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    GrpcCompressionOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }
}
