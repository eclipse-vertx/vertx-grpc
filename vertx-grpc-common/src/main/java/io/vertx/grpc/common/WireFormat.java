/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.common;

/**
 * The serialization format of gRPC messages on the wire.
 * <p>
 * Concrete instances are produced via the {@link ProtobufWireFormat} and {@link JsonReaderConfig}
 * subtypes. Instances are compared by {@link #canonicalName()}, so subtypes carrying additional
 * configuration (e.g. a customized {@link JsonReaderConfig}) match the canonical constants
 * for dispatch purposes while still threading their configuration through encode/decode.
 */
public enum WireFormat {

  /**
   * Canonical Protobuf wire format.
   */
  PROTOBUF(),

  /**
   * Canonical JSON wire format.
   */
  JSON();

}
