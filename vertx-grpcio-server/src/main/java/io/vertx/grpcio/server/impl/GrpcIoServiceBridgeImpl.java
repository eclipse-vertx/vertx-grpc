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
package io.vertx.grpcio.server.impl;

import com.google.protobuf.Descriptors;
import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Compressor;
import io.grpc.CompressorRegistry;
import io.grpc.Decompressor;
import io.grpc.DecompressorRegistry;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.common.GrpcError;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.impl.*;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.impl.GrpcServerResponseImpl;
import io.vertx.grpcio.common.impl.BridgeMessageDecoder;
import io.vertx.grpcio.common.impl.BridgeMessageEncoder;
import io.vertx.grpcio.common.impl.ReadStreamAdapter;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class GrpcIoServiceBridgeImpl implements GrpcIoServiceBridge {

  private final ServiceName serviceName;
  private final ServerServiceDefinition serviceDef;
  private final ProtoServiceDescriptorSupplier protoServiceDescriptorSupplier;

  public GrpcIoServiceBridgeImpl(ServerServiceDefinition serviceDef) {

    Object schemaDesc = serviceDef.getServiceDescriptor().getSchemaDescriptor();
    if (!(schemaDesc instanceof ProtoServiceDescriptorSupplier)) {
      throw new IllegalArgumentException("Service definition must be a ProtoMethodDescriptorSupplier");
    }
    ProtoServiceDescriptorSupplier supplier = (ProtoServiceDescriptorSupplier) schemaDesc;
    if(supplier.getFileDescriptor() == null) {
      throw new IllegalArgumentException("Service definition must have a FileDescriptor");
    }

    this.protoServiceDescriptorSupplier = supplier;
    this.serviceName = ServiceName.create(serviceDef.getServiceDescriptor().getName());
    this.serviceDef = serviceDef;
  }

  @Override
  public ServiceName name() {
    return serviceName;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return protoServiceDescriptorSupplier.getServiceDescriptor();
  }

  @Override
  public void unbind(GrpcIoServer server) {
    serviceDef.getMethods().forEach(m -> unbind(server, m));
  }

  private <Req, Resp> void unbind(GrpcIoServer server, ServerMethodDefinition<Req, Resp> methodDef) {
    server.callHandler(methodDef.getMethodDescriptor(), null);
  }

  @Override
  public void bind(GrpcIoServer server) {
    serviceDef.getMethods().forEach(m -> bind(server, m));
  }

  private <Req, Resp> void bind(GrpcIoServer server, ServerMethodDefinition<Req, Resp> methodDef) {
    server.callHandler(methodDef.getMethodDescriptor(), req -> {
      ServerCallHandler<Req, Resp> callHandler = methodDef.getServerCallHandler();
      Context context = Context.current();
      if (req.timeout() > 0L) {
        Context.CancellableContext cancellable = context.withDeadlineAfter(req.timeout(), TimeUnit.MILLISECONDS, new VertxScheduledExecutorService(Vertx.currentContext()));
        context = cancellable;
        context.addListener(context1 -> ((GrpcServerResponseImpl)req.response()).handleTimeout(), new Executor() {
          @Override
          public void execute(Runnable command) {
            command.run();
          }
        });
      }
      Context theContext = context;
      Runnable task = theContext.wrap(() -> {
        ServerCallImpl<Req, Resp> call = new ServerCallImpl<>(theContext, req, methodDef);
        ServerCall.Listener<Req> listener = callHandler.startCall(call, io.vertx.grpcio.common.impl.Utils.readMetadata(req.headers()));
        call.init(listener);
      });
      task.run();
    });
  }

  private static class ServerCallImpl<Req, Resp> extends ServerCall<Req, Resp> {

    private final Context context;
    private final GrpcServerRequest<Req, Resp> req;
    private final ServerMethodDefinition<Req, Resp> methodDef;
    private final ReadStreamAdapter<Req> readAdapter;
    private final WriteStreamAdapter<Resp> writeAdapter;
    private ServerCall.Listener<Req> listener;
    private final Decompressor decompressor;
    private Compressor compressor;
    private boolean halfClosed;
    private boolean closed;
    private boolean cancelled;
    private int messagesSent;
    private final Attributes attributes;

    public ServerCallImpl(Context context, GrpcServerRequest<Req, Resp> req, ServerMethodDefinition<Req, Resp> methodDef) {

      String encoding = req.encoding();


      this.context = context;
      this.decompressor = DecompressorRegistry.getDefaultInstance().lookupDecompressor(encoding);
      this.req = req;
      this.methodDef = methodDef;
      this.readAdapter = new ReadStreamAdapter<Req>() {
        @Override
        protected void handleClose() {
          halfClosed = true;
          Context previous = context.attach();
          try {
            listener.onHalfClose();
          } finally {
            context.detach(previous);
          }
        }
        @Override
        protected void handleMessage(Req msg) {
          if (!closed) {
            Context previous = context.attach();
            try {
              listener.onMessage(msg);
            } finally {
              context.detach(previous);
            }
          }
        }
      };
      this.writeAdapter = new WriteStreamAdapter<Resp>() {
        @Override
        protected void handleReady() {
          Context previous = context.attach();
          try {
            listener.onReady();
          } finally {
            context.detach(previous);
          }
        }
      };
      this.attributes = createAttributes();
    }

    void init(ServerCall.Listener<Req> listener) {
      this.listener = listener;
      req.errorHandler(error -> {
        if (error == GrpcError.CANCELLED && !closed) {
          cancelled = true;
          listener.onCancel();
        }
      });
      readAdapter.init(req, new BridgeMessageDecoder<>(methodDef.getMethodDescriptor().getRequestMarshaller(), decompressor));
      writeAdapter.init(req.response(), req.format(), new BridgeMessageEncoder<>(methodDef.getMethodDescriptor().getResponseMarshaller(), compressor));
    }

    private Attributes createAttributes() {
      Attributes.Builder builder = Attributes.newBuilder();
      SocketAddress remoteAddr = req.connection().remoteAddress();
      if (remoteAddr != null && remoteAddr.isInetSocket()) {
        try {
          InetAddress address = InetAddress.getByName(remoteAddr.hostAddress());
          builder.set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, new InetSocketAddress(address, remoteAddr.port()));
        } catch (UnknownHostException ignored) {
        }
      }
      SocketAddress localAddr = req.connection().localAddress();
      if (localAddr != null && localAddr.isInetSocket()) {
        try {
          InetAddress address = InetAddress.getByName(localAddr.hostAddress());
          builder.set(Grpc.TRANSPORT_ATTR_LOCAL_ADDR, new InetSocketAddress(address, localAddr.port()));
        } catch (UnknownHostException ignored) {
        }
      }
      if (req.connection().isSsl()) {
        builder.set(Grpc.TRANSPORT_ATTR_SSL_SESSION, req.connection().sslSession());
      }
      return builder.build();
    }

    @Override
    public boolean isReady() {
      return writeAdapter.isReady();
    }

    @Override
    public void request(int numMessages) {
      readAdapter.request(numMessages);
    }

    @Override
    public void sendHeaders(Metadata headers) {
      GrpcServerResponse<Req, Resp> response = req.response();
      io.vertx.grpcio.common.impl.Utils.writeMetadata(headers, response.headers());
      response.writeHead();
    }

    @Override
    public void sendMessage(Resp message) {
      messagesSent++;
      writeAdapter.write(message);
    }

    @Override
    public void close(Status status, Metadata trailers) {
      if (closed) {
        throw new IllegalStateException("Already closed");
      }
      closed = true;
      readAdapter.request(Integer.MAX_VALUE);
      GrpcServerResponse<Req, Resp> response = req.response();
      if (status == Status.OK && methodDef.getMethodDescriptor().getType().serverSendsOneMessage() && messagesSent == 0) {
        response.status(GrpcStatus.UNAVAILABLE).end();
      } else {
        io.vertx.grpcio.common.impl.Utils.writeMetadata(trailers, response.trailers());
        response.status(GrpcStatus.valueOf(status.getCode().value()));
        response.statusMessage(status.getDescription());
        response.end();
      }
      listener.onComplete();
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public MethodDescriptor<Req, Resp> getMethodDescriptor() {
      return methodDef.getMethodDescriptor();
    }

    @Override
    public void setCompression(String encoding) {
      compressor = CompressorRegistry.getDefaultInstance().lookupCompressor(encoding);
      GrpcServerResponse<Req, Resp> response = req.response();
      if (response.acceptedEncodings().contains(encoding)) {
        response.encoding(encoding);
      }
    }

    @Override
    public void setMessageCompression(boolean enabled) {
      // ????
      super.setMessageCompression(enabled);
    }

    @Override
    public Attributes getAttributes() {
      return this.attributes;
    }
  }
}
