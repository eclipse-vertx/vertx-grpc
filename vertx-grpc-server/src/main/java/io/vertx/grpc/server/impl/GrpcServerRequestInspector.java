package io.vertx.grpc.server.impl;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GrpcServerRequestInspector {

  private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("application/grpc(-web(-text)?)?(\\+(json|proto))?");

  private GrpcServerRequestInspector() {
  }

  public static RequestInspectionDetails inspect(HttpServerRequest request) {
    RequestInspectionDetailsBuilder builder = new RequestInspectionDetailsBuilder().version(request.version());
    if (!determineContentType(request.getHeader(HttpHeaders.CONTENT_TYPE), builder)) {
      return null;
    }

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

  public static final class RequestInspectionDetails {
    final HttpVersion version;
    final GrpcProtocol protocol;
    final WireFormat format;

    private RequestInspectionDetails(HttpVersion version, GrpcProtocol protocol, WireFormat format) {
      this.version = version;
      this.protocol = protocol;
      this.format = format;
    }
  }

  private static final class RequestInspectionDetailsBuilder {
    private HttpVersion version;
    private GrpcProtocol protocol;
    private WireFormat format;

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

    RequestInspectionDetails build() {
      return new RequestInspectionDetails(version, protocol, format);
    }
  }
}
