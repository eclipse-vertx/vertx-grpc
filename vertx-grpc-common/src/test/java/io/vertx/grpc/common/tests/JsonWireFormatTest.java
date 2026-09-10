package io.vertx.grpc.common.tests;

import io.vertx.grpc.common.JsonReaderConfig;
import io.vertx.grpc.common.JsonWriterConfig;
import io.vertx.grpc.common.WireFormat;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class JsonWireFormatTest {

  @Test
  public void testDefaultFlagsAreFalse() {
    assertFalse(JsonWriterConfig.DEFAULT.alwaysPrintFieldsWithNoPresence());
    assertFalse(JsonWriterConfig.DEFAULT.omittingInsignificantWhitespace());
    assertFalse(JsonWriterConfig.DEFAULT.preservingProtoFieldNames());
    assertFalse(JsonWriterConfig.DEFAULT.printingEnumsAsInts());
    assertFalse(JsonWriterConfig.DEFAULT.sortingMapKeys());
    assertFalse(JsonReaderConfig.DEFAULT.ignoringUnknownFields());
  }

  @Test
  public void testWriterFlagsAreImmutable() {
    JsonWriterConfig original = JsonWriterConfig.DEFAULT;
    JsonWriterConfig derived = original.alwaysPrintFieldsWithNoPresence(true);
    assertNotSame(original, derived);
    assertFalse(original.alwaysPrintFieldsWithNoPresence());
    assertTrue(derived.alwaysPrintFieldsWithNoPresence());
  }

  @Test
  public void testReaderFlagsAreImmutable() {
    JsonReaderConfig original = JsonReaderConfig.DEFAULT;
    JsonReaderConfig derived = original.ignoringUnknownFields(true);
    assertNotSame(original, derived);
    assertFalse(original.ignoringUnknownFields());
    assertTrue(derived.ignoringUnknownFields());
  }

  @Test
  public void testFlagsCompose() {
    JsonWriterConfig fmt = JsonWriterConfig.DEFAULT
      .alwaysPrintFieldsWithNoPresence(true)
      .printingEnumsAsInts(true);
    assertTrue(fmt.alwaysPrintFieldsWithNoPresence());
    assertTrue(fmt.printingEnumsAsInts());
    assertFalse(fmt.sortingMapKeys());
  }

  @Ignore
  @Test
  public void testEqualsByName() {
    JsonReaderConfig custom = JsonReaderConfig.DEFAULT.ignoringUnknownFields(true);
    JsonReaderConfig defaultJson = JsonReaderConfig.DEFAULT;
    assertEquals(defaultJson, custom);
    assertEquals(custom, defaultJson);
    assertEquals(defaultJson.hashCode(), custom.hashCode());
  }

  @Test
  public void testNotEqualsAcrossFormats() {
    WireFormat json = WireFormat.JSON;
    WireFormat proto = WireFormat.PROTOBUF;
    assertNotEquals(json, proto);
    assertNotEquals(proto, json);
  }
}
