package io.vertx.grpc.plugin.generation.generators;

import io.vertx.grpc.plugin.generation.*;
import io.vertx.grpc.plugin.template.TemplateEngine;
import io.vertx.grpc.plugin.template.TemplateException;

/**
 * Abstract base class for a code generator that produces generated files based on a specific type of generation context.
 * <p>
 * This class provides common functionalities such as file creation, relative path calculation, and integration with a template engine to facilitate rendering templates into
 * generated files. Concrete implementations should extend this class and provide specific logic for the code generation process.
 */
public abstract class CodeGenerator {

  protected final TemplateEngine templateEngine;
  protected final GenerationType generationType;

  protected CodeGenerator(TemplateEngine templateEngine, GenerationType generationType) {
    this.templateEngine = templateEngine;
    this.generationType = generationType;
  }

  /**
   * Generates code or other artifacts based on the provided generation context.
   *
   * @param context the context containing the necessary information for generation, including services, options, and other metadata
   * @return a {@link GenerationResult} containing the result of the generation process, which includes generated files, errors, and success status
   */
  public abstract GenerationResult generate(GenerationContext context);

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
