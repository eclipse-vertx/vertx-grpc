package io.vertx.grpc.plugin.generation.generators;

import io.vertx.grpc.plugin.generation.GeneratedFile;
import io.vertx.grpc.plugin.generation.GenerationContext;
import io.vertx.grpc.plugin.generation.GenerationException;
import io.vertx.grpc.plugin.generation.GenerationType;
import io.vertx.grpc.plugin.template.TemplateEngine;
import io.vertx.grpc.plugin.template.TemplateException;

/**
 * Abstract base class for a code generator that produces generated files based on a specific type of generation context.
 * <p>
 * This class provides common functionalities such as file creation, relative path calculation, and integration with a template engine to facilitate rendering templates into
 * generated files. Concrete implementations should extend this class and provide specific logic for the code generation process.
 *
 * @param <T> the type of generation context required by the generator, which must extend {@link GenerationContext}
 */
public abstract class AbstractCodeGenerator<T extends GenerationContext> implements CodeGenerator<T> {

  protected final TemplateEngine templateEngine;
  protected final GenerationType generationType;

  protected AbstractCodeGenerator(TemplateEngine templateEngine, GenerationType generationType) {
    this.templateEngine = templateEngine;
    this.generationType = generationType;
  }

  protected GeneratedFile createFile(String fileName, String relativePath, String templateName, Object templateContext) {
    try {
      String content = templateEngine.render(templateName, templateContext);
      return new GeneratedFile(fileName, relativePath, content, generationType);
    } catch (TemplateException e) {
      throw new GenerationException("Failed to generate file: " + fileName, e);
    }
  }

  protected String getRelativePath(String packageName) {
    return packageName.replace('.', '/');
  }
}
