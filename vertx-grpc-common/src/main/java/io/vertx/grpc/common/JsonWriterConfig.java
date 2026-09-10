package io.vertx.grpc.common;

import io.vertx.codegen.annotations.DataObject;

/**
 * Carries the protobuf-aware writer flags that the JSON encoder consults.
 * <p>
 * Instances are immutable. Each flag setter returns a new instance with that flag updated:
 * <pre>
 *   JsonWriterConfig verbose = JsonWriterConfig.DEFAULT
 *     .alwaysPrintFieldsWithNoPresence(true);
 * </pre>
 */
@DataObject
public class JsonWriterConfig {

  public static final JsonWriterConfig DEFAULT = new JsonWriterConfig();

  private static final JsonWriterConfig[] CACHE = new JsonWriterConfig[1 << Flag.values().length];

  private final byte flags;

  private JsonWriterConfig() {
    this((byte) 0);
  }

  private JsonWriterConfig(byte flags) {
    this.flags = flags;
  }

  static JsonWriterConfig of(int flags) {
    JsonWriterConfig fmt = CACHE[flags];
    if (fmt == null) {
      fmt = new JsonWriterConfig((byte) flags);
      CACHE[flags] = fmt;
    }
    return fmt;
  }

  private boolean isSet(Flag flag) {
    return (flags & flag.mask) != 0;
  }

  private JsonWriterConfig with(Flag flag, boolean value) {
    return of(value ? flags | flag.mask : flags & ~flag.mask);
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
  public JsonWriterConfig alwaysPrintFieldsWithNoPresence(boolean value) {
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
  public JsonWriterConfig omittingInsignificantWhitespace(boolean value) {
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
  public JsonWriterConfig preservingProtoFieldNames(boolean value) {
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
  public JsonWriterConfig printingEnumsAsInts(boolean value) {
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
  public JsonWriterConfig sortingMapKeys(boolean value) {
    return with(Flag.SORTING_MAP_KEYS, value);
  }

  private enum Flag {
    ALWAYS_PRINT_FIELDS_WITH_NO_PRESENCE("alwaysPrintFieldsWithNoPresence"),
    OMITTING_INSIGNIFICANT_WHITESPACE("omittingInsignificantWhitespace"),
    PRESERVING_PROTO_FIELD_NAMES("preservingProtoFieldNames"),
    PRINTING_ENUMS_AS_INTS("printingEnumsAsInts"),
    SORTING_MAP_KEYS("sortingMapKeys");

    final String key;
    final int mask;

    Flag(String key) {
      this.key = key;
      this.mask = 1 << ordinal();
    }
  }
}
