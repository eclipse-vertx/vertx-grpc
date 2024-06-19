package io.vertx.grpc.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ProcessHelper {

  private ProcessHelper() {}

  public static Process exec(Class<?> main, List<String> args) throws IOException {

    List<String> command = new ArrayList<>();
    // java binary executable
    command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    command.add("-cp");
    // inherit classpath
    command.add(System.getProperty("java.class.path"));
    // main class name
    command.add(main.getName());
    // args
    command.addAll(Objects.requireNonNullElse(args, Collections.emptyList()));

    return new ProcessBuilder(command).inheritIO().start();
  }
}
