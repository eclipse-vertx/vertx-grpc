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
package io.vertx.grpc.common.impl;

import io.vertx.grpc.common.ServiceName;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrpcMethod {

  private static final String PROTO_PACKAGE_NAME_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*";
  private static final String SERVICE_NAME_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";
  private static final String METHOD_NAME_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";
  private static final Pattern PATH_REGEX = Pattern.compile("/(?:(" + PROTO_PACKAGE_NAME_REGEX + ")\\.)?(" + SERVICE_NAME_REGEX + ")/(" + METHOD_NAME_REGEX + ")");

  private final String path;
  private final String fullMethodName;
  private final ServiceName serviceName;
  private final String methodName;

  public GrpcMethod(String path) {

    Matcher matcher = PATH_REGEX.matcher(path);
    ServiceName serviceName;
    String methodName;
    if (matcher.matches()) {
      serviceName = ServiceName.create(matcher.group(1), matcher.group(2));
      methodName = matcher.group(3);
    } else {
      serviceName = null;
      methodName = null;
    }

    this.path = path;
    this.fullMethodName = path.substring(1);
    this.serviceName = serviceName;
    this.methodName = methodName;
  }

  public String path() {
    return path;
  }

  public String fullMethodName() {
    return fullMethodName;
  }

  public ServiceName serviceName() {
    return serviceName;
  }

  public String methodName() {
    return methodName;
  }
}
