package io.vertx.tests.common;

import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.WireFormat;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class JsonWireFormatTest {

  @Test
  public void testDefaultFlagsAreFalse() {
    JsonWireFormat fmt = WireFormat.JSON;
    assertFalse(fmt.alwaysPrintFieldsWithNoPresence());
    assertFalse(fmt.omittingInsignificantWhitespace());
    assertFalse(fmt.preservingProtoFieldNames());
    assertFalse(fmt.printingEnumsAsInts());
    assertFalse(fmt.sortingMapKeys());
    assertFalse(fmt.ignoringUnknownFields());
  }

  @Test
  public void testWriterFlagsAreImmutable() {
    JsonWireFormat original = WireFormat.JSON;
    JsonWireFormat derived = original.alwaysPrintFieldsWithNoPresence(true);
    assertNotSame(original, derived);
    assertFalse(original.alwaysPrintFieldsWithNoPresence());
    assertTrue(derived.alwaysPrintFieldsWithNoPresence());
    assertFalse(derived.ignoringUnknownFields());
  }

  @Test
  public void testReaderFlagsAreImmutable() {
    JsonWireFormat original = WireFormat.JSON;
    JsonWireFormat derived = original.ignoringUnknownFields(true);
    assertNotSame(original, derived);
    assertFalse(original.ignoringUnknownFields());
    assertTrue(derived.ignoringUnknownFields());
    assertFalse(derived.alwaysPrintFieldsWithNoPresence());
  }

  @Test
  public void testFlagsCompose() {
    JsonWireFormat fmt = WireFormat.JSON
      .alwaysPrintFieldsWithNoPresence(true)
      .ignoringUnknownFields(true)
      .printingEnumsAsInts(true);
    assertTrue(fmt.alwaysPrintFieldsWithNoPresence());
    assertTrue(fmt.ignoringUnknownFields());
    assertTrue(fmt.printingEnumsAsInts());
    assertFalse(fmt.sortingMapKeys());
  }

  @Test
  public void testEqualsByName() {
    JsonWireFormat custom = WireFormat.JSON.ignoringUnknownFields(true);
    JsonWireFormat defaultJson = WireFormat.JSON;
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
