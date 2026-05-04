package io.vertx.grpc.common;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * JSON {@link WireFormat}. Carries the protobuf-aware writer/reader flags that
 * {@link ProtobufJsonWriter} and {@link ProtobufJsonReader} consult.
 * <p>
 * Instances are immutable. Each flag setter returns a new instance with that flag updated:
 * <pre>
 *   JsonWireFormat verbose = WireFormat.JSON
 *     .alwaysPrintFieldsWithNoPresence(true)
 *     .ignoringUnknownFields(true);
 * </pre>
 */
@DataObject
public class JsonWireFormat implements WireFormat {

  public static final String NAME = "json";

  /**
   * Default {@code alwaysPrintFieldsWithNoPresence} flag = {@code false}.
   */
  public static final boolean DEFAULT_ALWAYS_PRINT_FIELDS_WITH_NO_PRESENCE = false;

  /**
   * Default {@code omittingInsignificantWhitespace} flag = {@code false}.
   */
  public static final boolean DEFAULT_OMITTING_INSIGNIFICANT_WHITESPACE = false;

  /**
   * Default {@code preservingProtoFieldNames} flag = {@code false}.
   */
  public static final boolean DEFAULT_PRESERVING_PROTO_FIELD_NAMES = false;

  /**
   * Default {@code printingEnumsAsInts} flag = {@code false}.
   */
  public static final boolean DEFAULT_PRINTING_ENUMS_AS_INTS = false;

  /**
   * Default {@code sortingMapKeys} flag = {@code false}.
   */
  public static final boolean DEFAULT_SORTING_MAP_KEYS = false;

  /**
   * Default {@code ignoringUnknownFields} flag = {@code false}.
   */
  public static final boolean DEFAULT_IGNORING_UNKNOWN_FIELDS = false;

  private final boolean alwaysPrintFieldsWithNoPresence;
  private final boolean omittingInsignificantWhitespace;
  private final boolean preservingProtoFieldNames;
  private final boolean printingEnumsAsInts;
  private final boolean sortingMapKeys;
  private final boolean ignoringUnknownFields;

  public JsonWireFormat() {
    this(
      DEFAULT_ALWAYS_PRINT_FIELDS_WITH_NO_PRESENCE,
      DEFAULT_OMITTING_INSIGNIFICANT_WHITESPACE,
      DEFAULT_PRESERVING_PROTO_FIELD_NAMES,
      DEFAULT_PRINTING_ENUMS_AS_INTS,
      DEFAULT_SORTING_MAP_KEYS,
      DEFAULT_IGNORING_UNKNOWN_FIELDS
    );
  }

  private JsonWireFormat(
    boolean alwaysPrintFieldsWithNoPresence,
    boolean omittingInsignificantWhitespace,
    boolean preservingProtoFieldNames,
    boolean printingEnumsAsInts,
    boolean sortingMapKeys,
    boolean ignoringUnknownFields
  ) {
    this.alwaysPrintFieldsWithNoPresence = alwaysPrintFieldsWithNoPresence;
    this.omittingInsignificantWhitespace = omittingInsignificantWhitespace;
    this.preservingProtoFieldNames = preservingProtoFieldNames;
    this.printingEnumsAsInts = printingEnumsAsInts;
    this.sortingMapKeys = sortingMapKeys;
    this.ignoringUnknownFields = ignoringUnknownFields;
  }

  public JsonWireFormat(JsonObject json) {
    this(
      json.getBoolean("alwaysPrintFieldsWithNoPresence", DEFAULT_ALWAYS_PRINT_FIELDS_WITH_NO_PRESENCE),
      json.getBoolean("omittingInsignificantWhitespace", DEFAULT_OMITTING_INSIGNIFICANT_WHITESPACE),
      json.getBoolean("preservingProtoFieldNames", DEFAULT_PRESERVING_PROTO_FIELD_NAMES),
      json.getBoolean("printingEnumsAsInts", DEFAULT_PRINTING_ENUMS_AS_INTS),
      json.getBoolean("sortingMapKeys", DEFAULT_SORTING_MAP_KEYS),
      json.getBoolean("ignoringUnknownFields", DEFAULT_IGNORING_UNKNOWN_FIELDS)
    );
  }

  @Override
  public final String name() {
    return NAME;
  }

  public boolean getAlwaysPrintFieldsWithNoPresence() {
    return alwaysPrintFieldsWithNoPresence;
  }

  /**
   * @return a copy of this format with {@code alwaysPrintFieldsWithNoPresence} set to {@code value}
   */
  public JsonWireFormat alwaysPrintFieldsWithNoPresence(boolean value) {
    return new JsonWireFormat(value, omittingInsignificantWhitespace, preservingProtoFieldNames, printingEnumsAsInts, sortingMapKeys, ignoringUnknownFields);
  }

  public boolean getOmittingInsignificantWhitespace() {
    return omittingInsignificantWhitespace;
  }

  /**
   * @return a copy of this format with {@code omittingInsignificantWhitespace} set to {@code value}
   */
  public JsonWireFormat omittingInsignificantWhitespace(boolean value) {
    return new JsonWireFormat(alwaysPrintFieldsWithNoPresence, value, preservingProtoFieldNames, printingEnumsAsInts, sortingMapKeys, ignoringUnknownFields);
  }

  public boolean getPreservingProtoFieldNames() {
    return preservingProtoFieldNames;
  }

  /**
   * @return a copy of this format with {@code preservingProtoFieldNames} set to {@code value}
   */
  public JsonWireFormat preservingProtoFieldNames(boolean value) {
    return new JsonWireFormat(alwaysPrintFieldsWithNoPresence, omittingInsignificantWhitespace, value, printingEnumsAsInts, sortingMapKeys, ignoringUnknownFields);
  }

  public boolean getPrintingEnumsAsInts() {
    return printingEnumsAsInts;
  }

  /**
   * @return a copy of this format with {@code printingEnumsAsInts} set to {@code value}
   */
  public JsonWireFormat printingEnumsAsInts(boolean value) {
    return new JsonWireFormat(alwaysPrintFieldsWithNoPresence, omittingInsignificantWhitespace, preservingProtoFieldNames, value, sortingMapKeys, ignoringUnknownFields);
  }

  public boolean getSortingMapKeys() {
    return sortingMapKeys;
  }

  /**
   * @return a copy of this format with {@code sortingMapKeys} set to {@code value}
   */
  public JsonWireFormat sortingMapKeys(boolean value) {
    return new JsonWireFormat(alwaysPrintFieldsWithNoPresence, omittingInsignificantWhitespace, preservingProtoFieldNames, printingEnumsAsInts, value, ignoringUnknownFields);
  }

  public boolean getIgnoringUnknownFields() {
    return ignoringUnknownFields;
  }

  /**
   * @return a copy of this format with {@code ignoringUnknownFields} set to {@code value}
   */
  public JsonWireFormat ignoringUnknownFields(boolean value) {
    return new JsonWireFormat(alwaysPrintFieldsWithNoPresence, omittingInsignificantWhitespace, preservingProtoFieldNames, printingEnumsAsInts, sortingMapKeys, value);
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("alwaysPrintFieldsWithNoPresence", alwaysPrintFieldsWithNoPresence)
      .put("omittingInsignificantWhitespace", omittingInsignificantWhitespace)
      .put("preservingProtoFieldNames", preservingProtoFieldNames)
      .put("printingEnumsAsInts", printingEnumsAsInts)
      .put("sortingMapKeys", sortingMapKeys)
      .put("ignoringUnknownFields", ignoringUnknownFields);
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
}
