package io.vertx.grpc.server.impl;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.grpc.common.GrpcCompressor;
import io.vertx.grpc.common.GrpcDecompressor;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.server.GrpcProtocol;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrpcServerRequestInspector {

  private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("application/grpc(-web(-text)?)?(\\+(json|proto))?");

  private final boolean compressionEnabled;
  private final Set<String> compressionAlgorithms;
  private final Set<String> decompressionAlgorithms;

  public GrpcServerRequestInspector(boolean compressionEnabled, Set<String> compressionAlgorithms, Set<String> decompressionAlgorithms) {
    this.compressionEnabled = compressionEnabled;
    this.compressionAlgorithms = compressionAlgorithms;
    this.decompressionAlgorithms = decompressionAlgorithms;
  }

  public RequestInspectionDetails inspect(HttpServerRequest request) {
    RequestInspectionDetailsBuilder builder = new RequestInspectionDetailsBuilder();
    if (!determineContentType(request.getHeader(HttpHeaders.CONTENT_TYPE), builder)) {
      return null;
    }

    return builder.build();
  }

  private boolean determineContentType(String contentType, RequestInspectionDetailsBuilder builder) {
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

  private boolean determineCompression(String encoding, RequestInspectionDetailsBuilder builder) {
    builder.compressionEnabled(compressionEnabled);
    if (!compressionEnabled || encoding == null) {
      return false;
    }

    for (String algorithm : compressionAlgorithms) {
      if (algorithm.equals(encoding)) {
        builder.compressionAlgorithms(Set.of(algorithm));
        return true;
      }
    }

    for (String algorithm : decompressionAlgorithms) {
      if (algorithm.equals(encoding)) {
        builder.decompressionAlgorithms(Set.of(algorithm));
        return true;
      }
    }

    return false;
  }

  public static final class RequestInspectionDetails {
    final GrpcProtocol protocol;
    final WireFormat format;
    final boolean compressionEnabled;
    final Set<String> compressionAlgorithms;
    final Set<String> decompressionAlgorithms;

    private RequestInspectionDetails(GrpcProtocol protocol, WireFormat format, boolean compressionEnabled, Set<String> compressionAlgorithms, Set<String> decompressionAlgorithms) {
      this.protocol = protocol;
      this.format = format;
      this.compressionEnabled = compressionEnabled;
      this.compressionAlgorithms = compressionAlgorithms;
      this.decompressionAlgorithms = decompressionAlgorithms;
    }
  }

  private static final class RequestInspectionDetailsBuilder {
    private GrpcProtocol protocol;
    private WireFormat format;
    private boolean compressionEnabled;
    private Set<String> compressionAlgorithms;
    private Set<String> decompressionAlgorithms;

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

    RequestInspectionDetailsBuilder compressionEnabled(boolean compressionEnabled) {
      this.compressionEnabled = compressionEnabled;
      return this;
    }

    RequestInspectionDetailsBuilder compressionAlgorithms(Set<String> compressionAlgorithms) {
      this.compressionAlgorithms = compressionAlgorithms;
      return this;
    }

    RequestInspectionDetailsBuilder decompressionAlgorithms(Set<String> decompressionAlgorithms) {
      this.decompressionAlgorithms = decompressionAlgorithms;
      return this;
    }

    RequestInspectionDetails build() {
      return new RequestInspectionDetails(protocol, format, compressionEnabled, compressionAlgorithms, decompressionAlgorithms);
    }
  }
}
