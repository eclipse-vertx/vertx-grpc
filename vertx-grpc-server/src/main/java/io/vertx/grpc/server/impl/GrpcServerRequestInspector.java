package io.vertx.grpc.server.impl;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class GrpcServerRequestInspector {

  private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("application/grpc(-web(-text)?)?(\\+(json|proto))?");
  private static final String DEFAULT_ENCODING = "identity";
  private static final String DEFAULT_ACCEPT_ENCODING = "identity";

  private GrpcServerRequestInspector() {
  }

  public static RequestInspectionDetails inspect(HttpServerRequest request) {
    RequestInspectionDetailsBuilder builder = new RequestInspectionDetailsBuilder().version(request.version());
    if (!determineContentType(request.getHeader(HttpHeaders.CONTENT_TYPE), builder)) {
      return null;
    }

    determineEncoding(request, builder);

    return builder.build();
  }

  private static boolean determineContentType(String contentType, RequestInspectionDetailsBuilder builder) {
    if (contentType != null) {
      Matcher matcher = CONTENT_TYPE_PATTERN.matcher(contentType);
      if (matcher.matches()) {
        if (matcher.group(1) != null) {
          builder.protocol(matcher.group(2) == null ? GrpcProtocol.WEB : GrpcProtocol.WEB_TEXT);
        } else {
          builder.protocol(GrpcProtocol.HTTP_2);
        }
        if (matcher.group(3) != null) {
          switch (matcher.group(4)) {
            case "proto":
              builder.format(WireFormat.PROTOBUF);
              break;
            case "json":
              builder.format(WireFormat.JSON);
              break;
            default:
              throw new UnsupportedOperationException("Not possible");
          }
        } else {
          builder.format(WireFormat.PROTOBUF);
        }
        return true;
      } else {
        if (GrpcProtocol.TRANSCODING.mediaType().equals(contentType)) {
          builder.protocol(GrpcProtocol.TRANSCODING);
          builder.format(WireFormat.JSON);
          return true;
        }
      }
    }

    return false;
  }

  private static void determineEncoding(HttpServerRequest request, RequestInspectionDetailsBuilder builder) {
    String encoding = request.getHeader(HttpHeaders.CONTENT_ENCODING);
    if (request.getHeader(GrpcHeaderNames.GRPC_ENCODING) != null) {
      encoding = request.getHeader(GrpcHeaderNames.GRPC_ENCODING);
    }

    String acceptEncoding = request.getHeader(HttpHeaders.ACCEPT_ENCODING);
    if (request.getHeader(GrpcHeaderNames.GRPC_ACCEPT_ENCODING) != null) {
      acceptEncoding = request.getHeader(GrpcHeaderNames.GRPC_ACCEPT_ENCODING);
    }

    if (encoding == null) {
      encoding = DEFAULT_ENCODING;
    }

    if (acceptEncoding == null) {
      acceptEncoding = DEFAULT_ACCEPT_ENCODING;
    }

    if(!acceptEncoding.contains(encoding)) {
      acceptEncoding += "," + encoding;
    }

    builder.encoding(encoding);
    builder.acceptEncodings(Arrays.stream(acceptEncoding.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet()));
  }

  static final class RequestInspectionDetails {
    final HttpVersion version;
    final GrpcProtocol protocol;
    final WireFormat format;
    final String encoding;
    final Set<String> acceptEncodings;

    RequestInspectionDetails(HttpVersion version, GrpcProtocol protocol, WireFormat format, String encoding, Set<String> acceptEncodings) {
      this.version = version;
      this.protocol = protocol;
      this.format = format;
      this.encoding = encoding;
      this.acceptEncodings = acceptEncodings;
    }
  }

  static final class RequestInspectionDetailsBuilder {
    private HttpVersion version;
    private GrpcProtocol protocol;
    private WireFormat format;
    private String encoding;
    private Set<String> acceptEncodings;

    RequestInspectionDetailsBuilder() {
    }

    RequestInspectionDetailsBuilder version(HttpVersion version) {
      this.version = version;
      return this;
    }

    RequestInspectionDetailsBuilder protocol(GrpcProtocol protocol) {
      this.protocol = protocol;
      return this;
    }

    RequestInspectionDetailsBuilder format(WireFormat format) {
      this.format = format;
      return this;
    }

    RequestInspectionDetailsBuilder encoding(String encoding) {
      this.encoding = encoding;
      return this;
    }

    RequestInspectionDetailsBuilder acceptEncodings(Set<String> acceptEncodings) {
      this.acceptEncodings = acceptEncodings;
      return this;
    }

    RequestInspectionDetails build() {
      return new RequestInspectionDetails(version, protocol, format, encoding, acceptEncodings);
    }
  }
}
