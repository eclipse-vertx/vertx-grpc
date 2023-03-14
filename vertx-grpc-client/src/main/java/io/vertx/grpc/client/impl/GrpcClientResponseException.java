package io.vertx.grpc.client.impl;

import io.vertx.grpc.common.GrpcStatus;

public class GrpcClientResponseException extends RuntimeException {

	private static final long serialVersionUID = 3746058966983097891L;

	private GrpcStatus status;

	public GrpcClientResponseException(GrpcStatus status) {
		super("Invalid gRPC status " + status);
		this.status = status;
	}

	public GrpcStatus status() {
		return status;
	}

}
