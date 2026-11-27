package io.vertx.grpc.common;

/**
 * Signals a message whose Compressed-Flag is set while the stream declares no {@code grpc-encoding}, or declares
 * {@code identity}. The gRPC compression spec requires such a message to fail the call with {@code INTERNAL}.
 *
 * @see <a href="https://github.com/grpc/grpc/blob/master/doc/compression.md#test-cases">gRPC compression, test cases</a>
 */
public final class InvalidCompressedFlagException extends InvalidMessageException {

    public InvalidCompressedFlagException(String encoding) {
        super(encoding == null
          ? "The message is compressed but the stream declares no grpc-encoding"
          : "The message is compressed but the stream declares grpc-encoding " + encoding);
    }
}
