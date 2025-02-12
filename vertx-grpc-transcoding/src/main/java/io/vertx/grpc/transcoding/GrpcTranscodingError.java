package io.vertx.grpc.transcoding;

public enum GrpcTranscodingError {

  CANCELLED(0x01, 408, "Request timeout"),
  UNKNOWN(0x02, 500, "Internal Server Error"),
  INVALID_ARGUMENT(0x03, 400, "Invalid Argument"),
  DEADLINE_EXCEEDED(0x04, 504, "Deadline Exceeded"),
  NOT_FOUND(0x05, 404, "Not Found"),
  ALREADY_EXISTS(0x06, 409, "Already Exists"),
  PERMISSION_DENIED(0x07, 403, "Permission Denied"),
  RESOURCE_EXHAUSTED(0x08, 429, "Resource Exhausted"),
  FAILED_PRECONDITION(0x09, 400, "Failed Precondition"),
  ABORTED(0x0a, 409, "Aborted"),
  OUT_OF_RANGE(0x0b, 400, "Out of Range"),
  UNIMPLEMENTED(0x0c, 501, "Unimplemented"),
  INTERNAL(0x0d, 500, "Internal Server Error"),
  UNAVAILABLE(0x0e, 503, "Service Unavailable"),
  DATA_LOSS(0x0f, 500, "Data Loss"),
  UNAUTHENTICATED(0x10, 401, "Unauthenticated");

  private final int http2StatusCode;
  private final int httpStatusCode;
  private final String message;

  GrpcTranscodingError(int http2StatusCode, int httpStatusCode, String message) {
    this.http2StatusCode = http2StatusCode;
    this.httpStatusCode = httpStatusCode;
    this.message = message;
  }

  public static GrpcTranscodingError fromHttp2Code(int code) {
    for (GrpcTranscodingError error : values()) {
      if (error.http2StatusCode == code) {
        return error;
      }
    }
    return UNKNOWN;
  }

  public int getHttp2StatusCode() {
    return http2StatusCode;
  }

  public int getHttpStatusCode() {
    return httpStatusCode;
  }

  public String getMessage() {
    return message;
  }
}
