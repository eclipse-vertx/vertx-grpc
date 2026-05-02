package io.vertx.grpc.common;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * JSON {@link WireFormat}. Carries {@link WriterConfig} and {@link ReaderConfig} that
 * protobuf-aware encoders and decoders use to drive {@link ProtobufJsonWriter} and
 * {@link ProtobufJsonReader}.
 * <p>
 * Instances are immutable. Use {@link #withWriterConfig(WriterConfig)} and
 * {@link #withReaderConfig(ReaderConfig)} to derive a configured copy.
 */
@DataObject
public class JsonWireFormat implements WireFormat {

  public static final String NAME = "json";

  private final WriterConfig writerConfig;
  private final ReaderConfig readerConfig;

  public JsonWireFormat() {
    this(new WriterConfig(), new ReaderConfig());
  }

  public JsonWireFormat(WriterConfig writerConfig, ReaderConfig readerConfig) {
    this.writerConfig = Objects.requireNonNull(writerConfig);
    this.readerConfig = Objects.requireNonNull(readerConfig);
  }

  public JsonWireFormat(JsonObject json) {
    this(
      new WriterConfig(json.getJsonObject("writerConfig", new JsonObject())),
      new ReaderConfig(json.getJsonObject("readerConfig", new JsonObject()))
    );
  }

  @Override
  public final String name() {
    return NAME;
  }

  /**
   * @return the writer-side configuration used when encoding protobuf messages as JSON
   */
  public WriterConfig writerConfig() {
    return writerConfig;
  }

  /**
   * @return the reader-side configuration used when decoding protobuf messages from JSON
   */
  public ReaderConfig readerConfig() {
    return readerConfig;
  }

  /**
   * @return a copy of this format using the given {@code writerConfig}
   */
  public JsonWireFormat withWriterConfig(WriterConfig writerConfig) {
    return new JsonWireFormat(writerConfig, readerConfig);
  }

  /**
   * @return a copy of this format using the given {@code readerConfig}
   */
  public JsonWireFormat withReaderConfig(ReaderConfig readerConfig) {
    return new JsonWireFormat(writerConfig, readerConfig);
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("writerConfig", writerConfig.toJson())
      .put("readerConfig", readerConfig.toJson());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WireFormat)) {
      return false;
    }
    return NAME.equals(((WireFormat) o).name());
  }

  @Override
  public int hashCode() {
    return NAME.hashCode();
  }

  @Override
  public String toString() {
    return NAME;
  }

  /**
   * Configuration for the JSON writer that mirrors stable protobuf-java-util
   * {@code JsonFormat.Printer} options.
   */
  @DataObject
  public static class WriterConfig {

    private boolean alwaysPrintFieldsWithNoPresence;
    private boolean omittingInsignificantWhitespace;
    private boolean preservingProtoFieldNames;
    private boolean printingEnumsAsInts;
    private boolean sortingMapKeys;

    public WriterConfig() {
    }

    public WriterConfig(WriterConfig other) {
      this.alwaysPrintFieldsWithNoPresence = other.alwaysPrintFieldsWithNoPresence;
      this.omittingInsignificantWhitespace = other.omittingInsignificantWhitespace;
      this.preservingProtoFieldNames = other.preservingProtoFieldNames;
      this.printingEnumsAsInts = other.printingEnumsAsInts;
      this.sortingMapKeys = other.sortingMapKeys;
    }

    public WriterConfig(JsonObject json) {
      this.alwaysPrintFieldsWithNoPresence = json.getBoolean("alwaysPrintFieldsWithNoPresence", false);
      this.omittingInsignificantWhitespace = json.getBoolean("omittingInsignificantWhitespace", false);
      this.preservingProtoFieldNames = json.getBoolean("preservingProtoFieldNames", false);
      this.printingEnumsAsInts = json.getBoolean("printingEnumsAsInts", false);
      this.sortingMapKeys = json.getBoolean("sortingMapKeys", false);
    }

    public boolean getAlwaysPrintFieldsWithNoPresence() {
      return alwaysPrintFieldsWithNoPresence;
    }

    public WriterConfig setAlwaysPrintFieldsWithNoPresence(boolean value) {
      this.alwaysPrintFieldsWithNoPresence = value;
      return this;
    }

    public boolean getOmittingInsignificantWhitespace() {
      return omittingInsignificantWhitespace;
    }

    public WriterConfig setOmittingInsignificantWhitespace(boolean value) {
      this.omittingInsignificantWhitespace = value;
      return this;
    }

    public boolean getPreservingProtoFieldNames() {
      return preservingProtoFieldNames;
    }

    public WriterConfig setPreservingProtoFieldNames(boolean value) {
      this.preservingProtoFieldNames = value;
      return this;
    }

    public boolean getPrintingEnumsAsInts() {
      return printingEnumsAsInts;
    }

    public WriterConfig setPrintingEnumsAsInts(boolean value) {
      this.printingEnumsAsInts = value;
      return this;
    }

    public boolean getSortingMapKeys() {
      return sortingMapKeys;
    }

    public WriterConfig setSortingMapKeys(boolean value) {
      this.sortingMapKeys = value;
      return this;
    }

    public JsonObject toJson() {
      return new JsonObject()
        .put("alwaysPrintFieldsWithNoPresence", alwaysPrintFieldsWithNoPresence)
        .put("omittingInsignificantWhitespace", omittingInsignificantWhitespace)
        .put("preservingProtoFieldNames", preservingProtoFieldNames)
        .put("printingEnumsAsInts", printingEnumsAsInts)
        .put("sortingMapKeys", sortingMapKeys);
    }
  }

  /**
   * Configuration for the JSON reader that mirrors stable protobuf-java-util
   * {@code JsonFormat.Parser} options.
   */
  @DataObject
  public static class ReaderConfig {

    private boolean ignoringUnknownFields;

    public ReaderConfig() {
    }

    public ReaderConfig(ReaderConfig other) {
      this.ignoringUnknownFields = other.ignoringUnknownFields;
    }

    public ReaderConfig(JsonObject json) {
      this.ignoringUnknownFields = json.getBoolean("ignoringUnknownFields", false);
    }

    public boolean getIgnoringUnknownFields() {
      return ignoringUnknownFields;
    }

    public ReaderConfig setIgnoringUnknownFields(boolean value) {
      this.ignoringUnknownFields = value;
      return this;
    }

    public JsonObject toJson() {
      return new JsonObject()
        .put("ignoringUnknownFields", ignoringUnknownFields);
    }
  }
}
