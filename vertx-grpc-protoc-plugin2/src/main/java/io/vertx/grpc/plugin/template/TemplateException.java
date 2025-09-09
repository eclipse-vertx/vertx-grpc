package io.vertx.grpc.plugin.template;

/**
 * Exception representing failures or errors that occur during template processing or rendering.
 * <p>
 * This exception is typically thrown when the template engine encounters a problem while attempting to render a template. These problems may include invalid template syntax,
 * missing templates, issues with contextual data, or underlying system errors during the rendering process.
 */
public class TemplateException extends Exception {
  public TemplateException(String message) {
    super(message);
  }

  public TemplateException(String message, Throwable cause) {
    super(message, cause);
  }
}
