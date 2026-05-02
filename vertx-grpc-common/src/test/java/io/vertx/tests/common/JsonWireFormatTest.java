package io.vertx.tests.common;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.JsonWireFormat;
import io.vertx.grpc.common.ProtobufWireFormat;
import io.vertx.grpc.common.WireFormat;
import io.vertx.tests.common.grpc.Request;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JsonWireFormatTest {

  @Test
  public void testName() {
    assertEquals("json", WireFormat.JSON.name());
    assertEquals("proto", WireFormat.PROTOBUF.name());
  }

  @Test
  public void testDefaultConfigsNonNull() {
    JsonWireFormat fmt = WireFormat.JSON;
    assertNotNull(fmt.writerConfig());
    assertNotNull(fmt.readerConfig());
  }

  @Test
  public void testWithWriterConfigIsImmutable() {
    JsonWireFormat original = WireFormat.JSON;
    JsonWireFormat.WriterConfig customPrinter = new JsonWireFormat.WriterConfig().setAlwaysPrintFieldsWithNoPresence(true);
    JsonWireFormat derived = original.withWriterConfig(customPrinter);
    assertNotSame(original, derived);
    assertNotSame(original.writerConfig(), derived.writerConfig());
    assertSame(customPrinter, derived.writerConfig());
    assertSame(original.readerConfig(), derived.readerConfig());
  }

  @Test
  public void testWithReaderConfigIsImmutable() {
    JsonWireFormat original = WireFormat.JSON;
    JsonWireFormat.ReaderConfig customParser = new JsonWireFormat.ReaderConfig().setIgnoringUnknownFields(true);
    JsonWireFormat derived = original.withReaderConfig(customParser);
    assertNotSame(original, derived);
    assertNotSame(original.readerConfig(), derived.readerConfig());
    assertSame(customParser, derived.readerConfig());
    assertSame(original.writerConfig(), derived.writerConfig());
  }

  @Test
  public void testEqualsByName() {
    JsonWireFormat custom = WireFormat.JSON.withReaderConfig(new JsonWireFormat.ReaderConfig().setIgnoringUnknownFields(true));
    JsonWireFormat defaultJson = WireFormat.JSON;
    assertEquals(defaultJson, custom);
    assertEquals(custom, defaultJson);
    assertEquals(defaultJson.hashCode(), custom.hashCode());
  }

  @Test
  public void testNotEqualsAcrossFormats() {
    assertNotEquals(WireFormat.JSON, WireFormat.PROTOBUF);
    assertNotEquals(WireFormat.PROTOBUF, WireFormat.JSON);
  }

  @Test
  public void testCustomPrinterFlowsThroughEncoder() throws Exception {
    GrpcMessageEncoder<Request> encoder = GrpcMessageEncoder.encoder();
    Request emptyRequest = Request.newBuilder().build();

    JsonWireFormat defaultJson = WireFormat.JSON;
    GrpcMessage defaultMsg = encoder.encode(emptyRequest, defaultJson);
    assertEquals(new JsonObject(), defaultMsg.payload().toJsonObject());

    JsonWireFormat verbose = defaultJson.withWriterConfig(new JsonWireFormat.WriterConfig().setAlwaysPrintFieldsWithNoPresence(true));
    GrpcMessage verboseMsg = encoder.encode(emptyRequest, verbose);
    assertEquals(new JsonObject().put("name", ""), verboseMsg.payload().toJsonObject());
  }

  @Test
  public void testCustomParserFlowsThroughDecoder() {
    GrpcMessageDecoder<Request> decoder = GrpcMessageDecoder.decoder(Request.newBuilder());

    Buffer payload = new JsonObject().put("name", "Julien").put("unknown", 42).toBuffer();

    JsonWireFormat strict = WireFormat.JSON;
    GrpcMessage strictMsg = GrpcMessage.message("identity", strict, payload);
    assertThrows(Exception.class, () -> decoder.decode(strictMsg));

    JsonWireFormat lenient = strict.withReaderConfig(new JsonWireFormat.ReaderConfig().setIgnoringUnknownFields(true));
    GrpcMessage lenientMsg = GrpcMessage.message("identity", lenient, payload);
    Request decoded = decoder.decode(lenientMsg);
    assertEquals("Julien", decoded.getName());
  }

  @Test
  public void testCustomFormatPropagatesViaEncodedMessage() throws Exception {
    GrpcMessageEncoder<Request> encoder = GrpcMessageEncoder.encoder();
    JsonWireFormat custom = WireFormat.JSON.withWriterConfig(new JsonWireFormat.WriterConfig().setAlwaysPrintFieldsWithNoPresence(true));

    GrpcMessage msg = encoder.encode(Request.newBuilder().setName("Julien").build(), custom);

    assertSame(custom, msg.format());
    assertTrue(msg.format() instanceof JsonWireFormat);
    assertFalse(msg.format() instanceof ProtobufWireFormat);
  }
}
