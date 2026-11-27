package io.vertx.grpc.common;

import io.vertx.grpc.common.impl.Utils;

/**
 * Signals a compressed message whose {@code grpc-encoding} cannot be decoded. The gRPC compression spec requires the
 * call to fail, with {@code UNIMPLEMENTED} on the server and {@code INTERNAL} on the client.
 *
 * @see <a href="https://github.com/grpc/grpc/blob/master/doc/compression.md#test-cases">gRPC compression, test cases</a>
 */
public final class UnsupportedMessageEncodingException extends InvalidMessageException {

    public UnsupportedMessageEncodingException(String encoding) {
        super("grpc-encoding " + encoding + " is not supported, supported: " + Utils.IDENTITY + ", " + Utils.GZIP);
    }
}
