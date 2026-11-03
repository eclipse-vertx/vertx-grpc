/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.common;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.http.HttpVersion;

/**
 * gRPC error, a subset of {@link GrpcStatus} elements.
 * <ul>
 *  <li>The list of codes is taken from <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#http2-transport-mapping">spec</a>.</ul>
 *  <li>And the list of http/2 codes is taken from <a href="https://datatracker.ietf.org/doc/html/rfc9113#name-error-codes">spec</a>.</ul>
 * </ul>
 */
@VertxGen
public enum GrpcError {

  INTERNAL(GrpcStatus.INTERNAL, 0x02, 0x0102),

  UNAVAILABLE(GrpcStatus.UNAVAILABLE, 0x07, 0x010B),

  CANCELLED(GrpcStatus.CANCELLED, 0x08, 0x010C),

  RESOURCE_EXHAUSTED(GrpcStatus.RESOURCE_EXHAUSTED, 0x0B, 0x0107),

  PERMISSION_DENIED(GrpcStatus.PERMISSION_DENIED, 0x0C, 0x0102);

  public final GrpcStatus status;
  public final long http2ResetCode;
  public final long http3ResetCode;

  GrpcError(GrpcStatus status, long http2ResetCode, long http3ResetCode) {
    this.status = status;
    this.http2ResetCode = http2ResetCode;
    this.http3ResetCode = http3ResetCode;
  }

  /**
   * @return the reset code for the specified HTTP {@code version}
   */
  public long resetCode(HttpVersion version) {
    return version == HttpVersion.HTTP_3 ? http3ResetCode : http2ResetCode;
  }

  /**
   * Map the HTTP/2 code to the gRPC error.
   *
   * @param code the HTTP/2 code
   * @return the gRPC error or {@code null} when none applies
   */
  public static GrpcError mapHttp2ErrorCode(long code) {
    switch ((int)code) {
      case 0x00:
        // NO_ERROR
      case 0x01:
        // PROTOCOL_ERROR
      case 0x02:
        // INTERNAL_ERROR
      case 0x03:
        // FLOW_CONTROL_ERROR
      case 0x04:
        // FRAME_SIZE_ERROR
      case 0x06:
        // FRAME_SIZE_ERROR
      case 0x09:
        // COMPRESSION_ERROR
        return GrpcError.INTERNAL;
      case 0x07:
        // REFUSED_STREAM
        return GrpcError.UNAVAILABLE;
      case 0x0A:
        // CONNECT_ERROR
      case 0x08:
        // CANCEL
        return GrpcError.CANCELLED;
      case 0x0B:
        // ENHANCE_YOUR_CALM
        return GrpcError.RESOURCE_EXHAUSTED;
      case 0x0C:
        // INADEQUATE_SECURITY
        return GrpcError.PERMISSION_DENIED;
      default:
        // STREAM_CLOSED;
        // HTTP_1_1_REQUIRED
        return null;
    }
  }

  /**
   * Map the HTTP/3 code to the gRPC error.
   *
   * @param code the HTTP/3 code
   * @return the gRPC error or {@code null} when none applies
   */
  public static GrpcError mapHttp3ErrorCode(long code) {
    switch ((int)code) {
      case 0x0107:
        // H3_EXCESSIVE_LOAD
        return GrpcError.RESOURCE_EXHAUSTED;
      case 0x010B:
        // H3_REQUEST_REJECTED
        return GrpcError.UNAVAILABLE;
      case 0x010C:
        // H3_REQUEST_CANCELLED
        return GrpcError.CANCELLED;
      case 0x0100:
        // H3_NO_ERROR
      case 0x0101:
        // H3_GENERAL_PROTOCOL_ERROR
      case 0x0102:
        // H3_INTERNAL_ERROR
      case 0x0103:
        // H3_STREAM_CREATION_ERROR
      case 0x0104:
        // H3_CLOSED_CRITICAL_STREAM
      case 0x0105:
        // H3_FRAME_UNEXPECTED
      case 0x0106:
        // H3_FRAME_ERROR
      case 0x0108:
        // H3_ID_ERROR
      case 0x0109:
        // H3_SETTINGS_ERROR
      case 0x010A:
        // H3_MISSING_SETTINGS
      case 0x010D:
        // H3_REQUEST_INCOMPLETE
      case 0x010E:
        // H3_MESSAGE_ERROR
      case 0x010F:
        // H3_CONNECT_ERROR
      case 0x0110:
        // H3_VERSION_FALLBACK
        return GrpcError.INTERNAL;
      default:
        return null;
    }
  }

  /**
   * Map the transport error code to the gRPC error.
   *
   * @param version the HTTP version
   * @param code the transport error code
   * @return the gRPC error or {@code null} when none applies
   */
  public static GrpcError mapErrorCode(HttpVersion version, long code) {
    return version == HttpVersion.HTTP_3 ? mapHttp3ErrorCode(code) : mapHttp2ErrorCode(code);
  }
}
