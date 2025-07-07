package io.vertx.grpc.common.proto.schema;

public class ScalarType implements Type {

  public static final ScalarType DOUBLE = new ScalarType(TypeID.DOUBLE, WireType.I64);
  public static final ScalarType FLOAT = new ScalarType(TypeID.FLOAT, WireType.I32);
  public static final ScalarType INT64 = new ScalarType(TypeID.INT64, WireType.VARINT);
  public static final ScalarType UINT64 = new ScalarType(TypeID.UINT64, WireType.VARINT);
  public static final ScalarType INT32 = new ScalarType(TypeID.INT32, WireType.VARINT);
  public static final ScalarType FIXED64 = new ScalarType(TypeID.FIXED64, WireType.I64);
  public static final ScalarType FIXED32 = new ScalarType(TypeID.FIXED32, WireType.I32);
  public static final ScalarType BOOL = new ScalarType(TypeID.BOOL, WireType.VARINT);
  public static final ScalarType STRING = new ScalarType(TypeID.STRING, WireType.LEN);
  public static final ScalarType BYTES = new ScalarType(TypeID.BYTES, WireType.LEN);
  public static final ScalarType UINT32 = new ScalarType(TypeID.UINT32, WireType.VARINT);
  public static final ScalarType SFIXED32 = new ScalarType(TypeID.SFIXED32, WireType.I32);
  public static final ScalarType SFIXED64 = new ScalarType(TypeID.SFIXED64, WireType.I64);
  public static final ScalarType SINT32 = new ScalarType(TypeID.SINT32, WireType.VARINT);
  public static final ScalarType SINT64 = new ScalarType(TypeID.SINT64, WireType.VARINT);

  private final TypeID id;
  private final WireType wireType;

  public ScalarType(TypeID id, WireType wireType) {
    this.id = id;
    this.wireType = wireType;
  }

  @Override
  public TypeID id() {
    return id;
  }

  @Override
  public WireType wireType() {
    return wireType;
  }

  @Override
  public String toString() {
    return "ScalarType[name=" + id.name() + "]";
  }
}
