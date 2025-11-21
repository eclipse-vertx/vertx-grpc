FROM alpine:3.19

ARG GRPC_HEALTH_PROBE_VERSION=v0.4.42

RUN apk add --no-cache curl && \
    curl -fsSL https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-amd64 \
    -o /usr/local/bin/grpc_health_probe && \
    chmod +x /usr/local/bin/grpc_health_probe && \
    apk del curl

ENTRYPOINT ["/usr/local/bin/grpc_health_probe"]
