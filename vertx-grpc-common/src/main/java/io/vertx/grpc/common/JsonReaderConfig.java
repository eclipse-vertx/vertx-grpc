package io.vertx.grpc.common;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Carries the protobuf-aware reader flags that the JSON decoder consults.
 * <p>
 * Instances are immutable. Each flag setter returns a new instance with that flag updated:
 * <pre>
 *   JsonReaderConfig verbose = JsonReaderConfig.DEFAULT
 *     .ignoringUnknownFields(true);
 * </pre>
 */
@DataObject
public class JsonReaderConfig {

  public static final JsonReaderConfig DEFAULT = new JsonReaderConfig();

  private static final JsonReaderConfig[] CACHE = new JsonReaderConfig[1 << Flag.values().length];

  private final byte flags;

  private JsonReaderConfig() {
    this((byte) 0);
  }

  private JsonReaderConfig(byte flags) {
    this.flags = flags;
  }

  static JsonReaderConfig of(int flags) {
    JsonReaderConfig fmt = CACHE[flags];
    if (fmt == null) {
      fmt = new JsonReaderConfig((byte) flags);
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

  private JsonReaderConfig with(Flag flag, boolean value) {
    return of(value ? flags | flag.mask : flags & ~flag.mask);
  }

  /**
   * @return whether unknown fields encountered while parsing are ignored rather than rejected
   */
  public boolean ignoringUnknownFields() {
    return isSet(Flag.IGNORING_UNKNOWN_FIELDS);
  }

  /**
   * @return a copy of this config with {@code ignoringUnknownFields} set to {@code value}
   */
  public JsonReaderConfig ignoringUnknownFields(boolean value) {
    return with(Flag.IGNORING_UNKNOWN_FIELDS, value);
  }

  private enum Flag {
    IGNORING_UNKNOWN_FIELDS("ignoringUnknownFields");

    final String key;
    final int mask;

    Flag(String key) {
      this.key = key;
      this.mask = 1 << ordinal();
    }
  }
}
