package io.vertx.grpc.eventbus.impl;

import io.vertx.core.ThreadingModel;
import io.vertx.core.internal.ContextInternal;
import io.vertx.grpc.common.WireFormat;

public class Utils {

  public static final String JSON_CANONICAL_NAME = "json";
  public static final String PROTOBUF_CANONICAL_NAME = "proto";
  private static final String[] WIRE_FORMAT_NAMES;

  static {
    String[] wireFormatNames = new String[WireFormat.values().length];
    wireFormatNames[WireFormat.JSON.ordinal()] = JSON_CANONICAL_NAME;
    wireFormatNames[WireFormat.PROTOBUF.ordinal()] = PROTOBUF_CANONICAL_NAME;
    WIRE_FORMAT_NAMES = wireFormatNames;
  }

  static String toCanonicalName(WireFormat format) {
    return WIRE_FORMAT_NAMES[format.ordinal()];
  }

  static WireFormat fromCanonicalName(String name) {
    switch (name) {
      case "json":
        return WireFormat.JSON;
      case "proto":
        return WireFormat.PROTOBUF;
      default:
        return null;
    }
  }

  static ContextInternal eventLoopCtx(ContextInternal context) {
    if (context.threadingModel() == ThreadingModel.EVENT_LOOP) {
      return context;
    } else {
      return context
        .toBuilder()
        .withThreadingModel(ThreadingModel.EVENT_LOOP)
        .build();
    }
  }
}
