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
  public void testName() {
    assertEquals("json", WireFormat.JSON.name());
    assertEquals("proto", WireFormat.PROTOBUF.name());
  }

  @Test
  public void testDefaultFlagsAreFalse() {
    JsonWireFormat fmt = WireFormat.JSON;
    assertFalse(fmt.getAlwaysPrintFieldsWithNoPresence());
    assertFalse(fmt.getOmittingInsignificantWhitespace());
    assertFalse(fmt.getPreservingProtoFieldNames());
    assertFalse(fmt.getPrintingEnumsAsInts());
    assertFalse(fmt.getSortingMapKeys());
    assertFalse(fmt.getIgnoringUnknownFields());
  }

  @Test
  public void testWriterFlagsAreImmutable() {
    JsonWireFormat original = WireFormat.JSON;
    JsonWireFormat derived = original.alwaysPrintFieldsWithNoPresence(true);
    assertNotSame(original, derived);
    assertFalse(original.getAlwaysPrintFieldsWithNoPresence());
    assertTrue(derived.getAlwaysPrintFieldsWithNoPresence());
    assertFalse(derived.getIgnoringUnknownFields());
  }

  @Test
  public void testReaderFlagsAreImmutable() {
    JsonWireFormat original = WireFormat.JSON;
    JsonWireFormat derived = original.ignoringUnknownFields(true);
    assertNotSame(original, derived);
    assertFalse(original.getIgnoringUnknownFields());
    assertTrue(derived.getIgnoringUnknownFields());
    assertFalse(derived.getAlwaysPrintFieldsWithNoPresence());
  }

  @Test
  public void testFlagsCompose() {
    JsonWireFormat fmt = WireFormat.JSON
      .alwaysPrintFieldsWithNoPresence(true)
      .ignoringUnknownFields(true)
      .printingEnumsAsInts(true);
    assertTrue(fmt.getAlwaysPrintFieldsWithNoPresence());
    assertTrue(fmt.getIgnoringUnknownFields());
    assertTrue(fmt.getPrintingEnumsAsInts());
    assertFalse(fmt.getSortingMapKeys());
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
