package io.vertx.grpc.common;

/**
 * Protobuf {@link WireFormat}. Extend or instantiate this class to carry configuration that
 * encoders and decoders can consult while still matching {@link WireFormat#PROTOBUF} via
 * {@link #equals(Object)}.
 */
public class ProtobufWireFormat implements WireFormat {

  public static final String NAME = "proto";

  @Override
  public final String name() {
    return NAME;
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
