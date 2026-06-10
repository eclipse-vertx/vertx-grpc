package io.vertx.grpc.common;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * JSON {@link WireFormat}. Carries the protobuf-aware writer/reader flags that the JSON
 * encoder and decoder consult.
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

  private static final String MEDIA_TYPE = "application/grpc+json";
  private static final JsonWireFormat[] CACHE = new JsonWireFormat[1 << Flag.values().length];

  private final byte flags;

  public JsonWireFormat() {
    this((byte) 0);
  }

  private JsonWireFormat(byte flags) {
    this.flags = flags;
  }

  public JsonWireFormat(JsonObject json) {
    this(read(json));
  }

  static JsonWireFormat of(int flags) {
    JsonWireFormat fmt = CACHE[flags];
    if (fmt == null) {
      fmt = new JsonWireFormat((byte) flags);
      CACHE[flags] = fmt;
    }
    return fmt;
  }

  private static byte read(JsonObject json) {
    int flags = 0;
    for (Flag flag : Flag.values()) {
      if (json.getBoolean(flag.key, false)) {
        flags |= flag.mask;
      }
    }
    return (byte) flags;
  }

  private boolean isSet(Flag flag) {
    return (flags & flag.mask) != 0;
  }

  private JsonWireFormat with(Flag flag, boolean value) {
    return of(value ? flags | flag.mask : flags & ~flag.mask);
  }

  @Override
  public final String name() {
    return NAME;
  }

  @Override
  public String mediaType() {
    return MEDIA_TYPE;
  }

  /**
   * @return whether fields without presence are always printed, including those left at their default value
   */
  public boolean alwaysPrintFieldsWithNoPresence() {
    return isSet(Flag.ALWAYS_PRINT_FIELDS_WITH_NO_PRESENCE);
  }

  /**
   * @return a copy of this format with {@code alwaysPrintFieldsWithNoPresence} set to {@code value}
   */
  public JsonWireFormat alwaysPrintFieldsWithNoPresence(boolean value) {
    return with(Flag.ALWAYS_PRINT_FIELDS_WITH_NO_PRESENCE, value);
  }

  /**
   * @return whether insignificant whitespace is omitted, producing a compact single-line output
   */
  public boolean omittingInsignificantWhitespace() {
    return isSet(Flag.OMITTING_INSIGNIFICANT_WHITESPACE);
  }

  /**
   * @return a copy of this format with {@code omittingInsignificantWhitespace} set to {@code value}
   */
  public JsonWireFormat omittingInsignificantWhitespace(boolean value) {
    return with(Flag.OMITTING_INSIGNIFICANT_WHITESPACE, value);
  }

  /**
   * @return whether the original proto field names are used instead of the lowerCamelCase JSON names
   */
  public boolean preservingProtoFieldNames() {
    return isSet(Flag.PRESERVING_PROTO_FIELD_NAMES);
  }

  /**
   * @return a copy of this format with {@code preservingProtoFieldNames} set to {@code value}
   */
  public JsonWireFormat preservingProtoFieldNames(boolean value) {
    return with(Flag.PRESERVING_PROTO_FIELD_NAMES, value);
  }

  /**
   * @return whether enum values are printed as their integer number instead of their name
   */
  public boolean printingEnumsAsInts() {
    return isSet(Flag.PRINTING_ENUMS_AS_INTS);
  }

  /**
   * @return a copy of this format with {@code printingEnumsAsInts} set to {@code value}
   */
  public JsonWireFormat printingEnumsAsInts(boolean value) {
    return with(Flag.PRINTING_ENUMS_AS_INTS, value);
  }

  /**
   * @return whether map entries are emitted with their keys sorted, for deterministic output
   */
  public boolean sortingMapKeys() {
    return isSet(Flag.SORTING_MAP_KEYS);
  }

  /**
   * @return a copy of this format with {@code sortingMapKeys} set to {@code value}
   */
  public JsonWireFormat sortingMapKeys(boolean value) {
    return with(Flag.SORTING_MAP_KEYS, value);
  }

  /**
   * @return whether unknown fields encountered while parsing are ignored rather than rejected
   */
  public boolean ignoringUnknownFields() {
    return isSet(Flag.IGNORING_UNKNOWN_FIELDS);
  }

  /**
   * @return a copy of this format with {@code ignoringUnknownFields} set to {@code value}
   */
  public JsonWireFormat ignoringUnknownFields(boolean value) {
    return with(Flag.IGNORING_UNKNOWN_FIELDS, value);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    for (Flag flag : Flag.values()) {
      json.put(flag.key, isSet(flag));
    }
    return json;
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

  private enum Flag {
    ALWAYS_PRINT_FIELDS_WITH_NO_PRESENCE("alwaysPrintFieldsWithNoPresence"),
    OMITTING_INSIGNIFICANT_WHITESPACE("omittingInsignificantWhitespace"),
    PRESERVING_PROTO_FIELD_NAMES("preservingProtoFieldNames"),
    PRINTING_ENUMS_AS_INTS("printingEnumsAsInts"),
    SORTING_MAP_KEYS("sortingMapKeys"),
    IGNORING_UNKNOWN_FIELDS("ignoringUnknownFields");

    final String key;
    final int mask;

    Flag(String key) {
      this.key = key;
      this.mask = 1 << ordinal();
    }
  }
}
