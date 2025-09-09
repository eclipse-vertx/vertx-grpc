package io.vertx.grpc.plugin.template;

/**
 * Represents a template rendering engine capable of processing templates and generating formatted output by merging a specified template with contextual data.
 * <p>
 * Implementations of this interface handle the rendering process using their underlying templating system. The specific behavior and syntax of template files depend on the
 * implementation.
 */
public interface TemplateEngine {
  /**
   * Renders a template identified by its name using the provided context to generate a formatted output.
   * <p>
   * Implementations of this method leverage a specific underlying templating engine to process the template and interpolate the contextual data.
   *
   * @param templateName the name of the template to process
   * @param context the contextual data to be used in the template rendering process
   * @return the rendered content as a string after merging the template with the provided context
   * @throws TemplateException if the rendering process fails due to template errors or processing issues
   */
  String render(String templateName, Object context) throws TemplateException;
}
