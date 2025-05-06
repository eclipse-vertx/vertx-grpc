package io.vertx.grpc.plugin;

/**
 * Options for the Vert.x gRPC code generator.
 * This class encapsulates all configuration options for the generator.
 */
public class VertxGrpcGeneratorOptions {

  private final boolean generateGrpcClient;
  private final boolean generateGrpcService;
  private final boolean generateGrpcIo;
  private final boolean generateTranscoding;
  private final String servicePrefix;

  /**
   * Creates a new instance with the specified options.
   *
   * @param generateGrpcClient whether to generate gRPC client code
   * @param generateGrpcService whether to generate gRPC service code
   * @param generateGrpcIo whether to generate gRPC IO code
   * @param generateTranscoding whether to generate transcoding options for methods with HTTP annotations
   * @param servicePrefix prefix to add to generated service names
   */
  private VertxGrpcGeneratorOptions(boolean generateGrpcClient, boolean generateGrpcService, boolean generateGrpcIo,
                                  boolean generateTranscoding, String servicePrefix) {
    this.generateGrpcClient = generateGrpcClient;
    this.generateGrpcService = generateGrpcService;
    this.generateGrpcIo = generateGrpcIo;
    this.generateTranscoding = generateTranscoding;
    this.servicePrefix = servicePrefix != null ? servicePrefix : "";
  }

  /**
   * Returns whether to generate gRPC client code.
   *
   * @return true if gRPC client code should be generated, false otherwise
   */
  public boolean isGenerateGrpcClient() {
    return generateGrpcClient;
  }

  /**
   * Returns whether to generate gRPC service code.
   *
   * @return true if gRPC service code should be generated, false otherwise
   */
  public boolean isGenerateGrpcService() {
    return generateGrpcService;
  }

  /**
   * Returns whether to generate gRPC IO code.
   *
   * @return true if gRPC IO code should be generated, false otherwise
   */
  public boolean isGenerateGrpcIo() {
    return generateGrpcIo;
  }

  /**
   * Returns whether to generate transcoding options for methods with HTTP annotations.
   *
   * @return true if transcoding options should be generated, false otherwise
   */
  public boolean isGenerateTranscoding() {
    return generateTranscoding;
  }

  /**
   * Returns the prefix to add to generated service names.
   *
   * @return the service prefix
   */
  public String getServicePrefix() {
    return servicePrefix;
  }

  /**
   * Creates a new builder for VertxGrpcGeneratorOptions.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for VertxGrpcGeneratorOptions.
   */
  public static class Builder {
    private boolean generateGrpcClient = true;
    private boolean generateGrpcService = true;
    private boolean generateGrpcIo = false;
    private boolean generateTranscoding = true;
    private String servicePrefix = "";

    /**
     * Sets whether to generate gRPC client code.
     *
     * @param generateGrpcClient true to generate gRPC client code, false otherwise
     * @return this builder
     */
    public Builder setGenerateGrpcClient(boolean generateGrpcClient) {
      this.generateGrpcClient = generateGrpcClient;
      return this;
    }

    /**
     * Sets whether to generate gRPC service code.
     *
     * @param generateGrpcService true to generate gRPC service code, false otherwise
     * @return this builder
     */
    public Builder setGenerateGrpcService(boolean generateGrpcService) {
      this.generateGrpcService = generateGrpcService;
      return this;
    }

    /**
     * Sets whether to generate gRPC IO code.
     *
     * @param generateGrpcIo true to generate gRPC IO code, false otherwise
     * @return this builder
     */
    public Builder setGenerateGrpcIo(boolean generateGrpcIo) {
      this.generateGrpcIo = generateGrpcIo;
      return this;
    }

    /**
     * Sets whether to generate transcoding options for methods with HTTP annotations.
     *
     * @param generateTranscoding true to generate transcoding options, false otherwise
     * @return this builder
     */
    public Builder setGenerateTranscoding(boolean generateTranscoding) {
      this.generateTranscoding = generateTranscoding;
      return this;
    }

    /**
     * Sets the prefix to add to generated service names.
     *
     * @param servicePrefix the service prefix
     * @return this builder
     */
    public Builder setServicePrefix(String servicePrefix) {
      this.servicePrefix = servicePrefix != null ? servicePrefix : "";
      return this;
    }

    /**
     * Builds a new VertxGrpcGeneratorOptions instance.
     *
     * @return a new VertxGrpcGeneratorOptions instance
     */
    public VertxGrpcGeneratorOptions build() {
      return new VertxGrpcGeneratorOptions(generateGrpcClient, generateGrpcService, generateGrpcIo, generateTranscoding, servicePrefix);
    }
  }
}
