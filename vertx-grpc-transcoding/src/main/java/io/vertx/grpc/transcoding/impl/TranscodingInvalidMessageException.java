package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.common.InvalidMessageException;

class TranscodingInvalidMessageException extends InvalidMessageException {

  TranscodingInvalidMessageException(Throwable cause) {
    super(cause);
  }
}
