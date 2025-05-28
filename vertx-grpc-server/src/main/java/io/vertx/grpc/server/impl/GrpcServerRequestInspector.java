package io.vertx.grpc.server.impl;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.grpc.common.GrpcCompressor;
import io.vertx.grpc.common.GrpcDecompressor;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrpcServerRequestInspector {

  private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("application/grpc(-web(-text)?)?(\\+(json|proto))?");

  private final Set<GrpcCompressor> compressors = GrpcCompressor.getDefaultCompressors();
  private final Set<GrpcDecompressor> decompressors = GrpcDecompressor.getDefaultDecompressors();

  public RequestInspectionDetails inspect(HttpServerRequest request) {
    RequestInspectionDetailsBuilder builder = new RequestInspectionDetailsBuilder();
    if (!determine(request.getHeader(HttpHeaders.CONTENT_TYPE), builder)) {
      return null;
    }

    return builder.build();
  }

  private boolean determine(String contentType, RequestInspectionDetailsBuilder builder) {
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

  public static class RequestInspectionDetails {
    final GrpcProtocol protocol;
    final WireFormat format;

    private RequestInspectionDetails(GrpcProtocol protocol, WireFormat format) {
      this.protocol = protocol;
      this.format = format;
    }
  }

  private static final class RequestInspectionDetailsBuilder {
    private GrpcProtocol protocol;
    private WireFormat format;

    RequestInspectionDetailsBuilder() {
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
      return new RequestInspectionDetails(protocol, format);
    }
  }
}
