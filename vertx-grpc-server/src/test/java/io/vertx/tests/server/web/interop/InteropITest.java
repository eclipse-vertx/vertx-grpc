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

package io.vertx.tests.server.web.interop;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.File;
import java.time.Duration;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

@RunWith(VertxUnitRunner.class)
public class InteropITest {

  private static final String GRPC_WEB_REPO_PATH = System.getProperty("grpc-web.repo.path");

  @Rule
  public Timeout rule = Timeout.millis(Duration.ofMinutes(10).toMillis());

  private Vertx vertx;

  @Before
  public void setUp(TestContext should) {
    vertx = Vertx.vertx();
    vertx.deployVerticle(new InteropServer()).onComplete(should.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext should) {
    vertx.close().onComplete(should.asyncAssertSuccess());
  }

  @Test
  public void interopTests() {
    assumeFalse("grpc-web repo path isn't defined", GRPC_WEB_REPO_PATH == null);

    File repoFile = new File(GRPC_WEB_REPO_PATH);
    assertTrue("grpc-web repo path doesn't denote a directory", repoFile.isDirectory());
    File dockerfile = new File(repoFile, "net/grpc/gateway/docker/prereqs/Dockerfile");
    assertTrue("Dockerfile doesn't exists or isn't a normal file", dockerfile.isFile());

    ImageFromDockerfile image = new ImageFromDockerfile()
      .withFileFromFile(".", repoFile)
      .withFileFromFile("Dockerfile", dockerfile);

    ToStringConsumer logConsumer = new ToStringConsumer();
    try (GenericContainer<?> container = new GenericContainer<>(image)) {
      container
        .withLogConsumer(logConsumer)
        .withNetworkMode("host")
        .withCommand("/bin/bash", "/github/grpc-web/scripts/docker-run-interop-tests.sh")
        .withStartupCheckStrategy(new OneShotStartupCheckStrategy())
        .start();
    } finally {
      System.out.println(logConsumer.toUtf8String());
    }
  }
}
