package io.vertx.grpc.plugin.template;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.StringWriter;

/**
 * Implementation of the TemplateEngine interface that uses the Mustache templating library to render templates. This engine processes Mustache templates by merging them with the
 * provided context data to generate formatted output.
 */
public class MustacheTemplateEngine implements TemplateEngine {

  private final MustacheFactory mustacheFactory;

  public MustacheTemplateEngine() {
    this.mustacheFactory = new DefaultMustacheFactory();
  }

  @Override
  public String render(String templateName, Object context) throws TemplateException {
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
