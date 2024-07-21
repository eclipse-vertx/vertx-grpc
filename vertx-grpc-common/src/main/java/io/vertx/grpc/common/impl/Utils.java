/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.common.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Utils {

  public static String utf8PercentEncode(String s) {
    try {
      return URLEncoder.encode(s, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("~", "%7E");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
