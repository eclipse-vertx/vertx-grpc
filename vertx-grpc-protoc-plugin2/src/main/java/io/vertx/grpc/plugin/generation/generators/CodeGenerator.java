package io.vertx.grpc.plugin.generation.generators;

import io.vertx.grpc.plugin.generation.GenerationContext;
import io.vertx.grpc.plugin.generation.GenerationResult;

/**
 * Interface defining the contract for a code generator.
 *
 * @param <T> the type of generation context required by the generator, which must extend {@link GenerationContext}
 */
public interface CodeGenerator<T extends GenerationContext> {
  /**
   * Generates code or other artifacts based on the provided generation context.
   *
   * @param context the context containing the necessary information for generation, including services, options, and other metadata
   * @return a {@link GenerationResult} containing the result of the generation process, which includes generated files, errors, and success status
   */
  GenerationResult generate(T context);
}
