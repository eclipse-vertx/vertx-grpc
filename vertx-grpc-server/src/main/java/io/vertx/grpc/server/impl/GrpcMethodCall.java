package io.vertx.grpc.server.impl;

import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.GrpcStream;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GrpcMethodCall<Req, Resp> {

  private static final String PROTO_PACKAGE_NAME_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*";
  private static final String SERVICE_NAME_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";
  private static final String METHOD_NAME_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";
  private static final Pattern PATH_REGEX = Pattern.compile("/(?:(" + PROTO_PACKAGE_NAME_REGEX + ")\\.)?(" + SERVICE_NAME_REGEX + ")/(" + METHOD_NAME_REGEX + ")");

  private final String path;
  private final String fullMethodName;
  private final ServiceName serviceName;
  private final String methodName;
  private final GrpcStream stream;
  private final GrpcMessageDecoder<Req> messageDecoder;
  private final GrpcMessageEncoder<Resp> messageEncoder;

  public GrpcMethodCall(String path,
                        GrpcStream stream,
                        GrpcMessageDecoder<Req> messageDecoder,
                        GrpcMessageEncoder<Resp> messageEncoder) {

    Matcher matcher = PATH_REGEX.matcher(path);
    ServiceName serviceName;
    String methodName;
    if (matcher.matches()) {
      serviceName = ServiceName.create(matcher.group(1), matcher.group(2));
      methodName = matcher.group(3);
    } else {
      serviceName = null;
      methodName = null;
    }

    this.path = path;
    this.fullMethodName = path.substring(1);
    this.serviceName = serviceName;
    this.methodName = methodName;
    this.stream = stream;
    this.messageDecoder = messageDecoder;
    this.messageEncoder = messageEncoder;
  }

  public String path() {
    return path;
  }

  public String fullMethodName() {
    return fullMethodName;
  }

  public ServiceName serviceName() {
    return serviceName;
  }

  public String methodName() {
    return methodName;
  }

  public GrpcStream stream() {
    return stream;
  }

  public GrpcMessageDecoder<Req> messageDecoder() {
    return messageDecoder;
  }

  public GrpcMessageEncoder<Resp> messageEncoder() {
    return messageEncoder;
  }
}
