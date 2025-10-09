package io.vertx.grpc.plugin.template;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.StringWriter;

/**
 * A utility class responsible for processing templates with contextual data to generate rendered output.
 * <p>
 * This class is designed to use the Mustache templating engine as its underlying mechanism for template processing. It provides methods for rendering templates with dynamic
 * content passed as context. The templates are identified by their names, and the output is generated as a string after context interpolation.
 */
public class TemplateEngine {

  private static final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

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
  public static String render(String templateName, Object context) throws TemplateException {
    try {
      Mustache mustache = mustacheFactory.compile(templateName);
      StringWriter writer = new StringWriter();
      mustache.execute(writer, context);
      return writer.toString();
    } catch (Exception e) {
      throw new TemplateException("Failed to render template: " + templateName, e);
    }
  }
}
