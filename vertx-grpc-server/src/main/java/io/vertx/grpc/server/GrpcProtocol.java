package io.vertx.grpc.server;

import io.vertx.core.http.HttpVersion;
import io.vertx.grpc.common.GrpcMediaType;
import io.vertx.grpc.common.WireFormat;

import java.util.EnumSet;

/**
 * Describe the underlying gRPC protocol.
 */
public enum GrpcProtocol {

  /**
   * gRPC over HTTP/2
   */
  HTTP_2("application/grpc", EnumSet.of(HttpVersion.HTTP_2)) {
    @Override
    public WireFormat wireFormat(String mediaType) {
      return GrpcMediaType.parseContentType(mediaType, mediaType());
    }
  },

  /**
   * gRPC transcoding HTTP/1
   */
  TRANSCODING("application/json", EnumSet.allOf(HttpVersion.class)) {
    @Override
    public WireFormat wireFormat(String mediaType) {
      return mediaType().equals(mediaType) ? WireFormat.JSON : null;
    }
  },

  /**
   * gRPC Web
   */
  WEB("application/grpc-web", EnumSet.allOf(HttpVersion.class)) {
    @Override
    public WireFormat wireFormat(String mediaType) {
      return GrpcMediaType.parseContentType(mediaType, mediaType());
    }
  },

  /**
   * gRPC Web text
   */
  WEB_TEXT("application/grpc-web-text", EnumSet.allOf(HttpVersion.class)) {
    @Override
    public WireFormat wireFormat(String mediaType) {
      return GrpcMediaType.parseContentType(mediaType, mediaType());
    }
  };

  private final String mediaType;
  private final EnumSet<HttpVersion> acceptedVersions;

  GrpcProtocol(String mediaType, EnumSet<HttpVersion> acceptedVersions) {
    this.mediaType = mediaType;
    this.acceptedVersions = acceptedVersions;
  }

  /**
   * @return whether the protocol accepts the HTTP {@code version}
   */
  public boolean accepts(HttpVersion version) {
    return acceptedVersions.contains(version);
  }

  /**
   * @return the HTTP media type
   */
  public String mediaType() {
    return mediaType;
  }

  /**
   * The wire format adapted for the
   * @param mediaType
   * @return
   */
  public abstract WireFormat wireFormat(String mediaType);

}
