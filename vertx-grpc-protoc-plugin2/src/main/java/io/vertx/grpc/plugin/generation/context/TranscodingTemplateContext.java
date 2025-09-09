package io.vertx.grpc.plugin.generation.context;

import io.vertx.grpc.plugin.descriptors.TranscodingDescriptor;

import java.util.List;
import java.util.stream.Collectors;

public class TranscodingTemplateContext {

  public String path;
  public String method;
  public String selector;
  public String body;
  public boolean option;
  public String responseBody;
  public List<TranscodingTemplateContext> additionalBindings;

  public static TranscodingTemplateContext fromTranscodingDescriptor(TranscodingDescriptor transcoding) {
    TranscodingTemplateContext context = new TranscodingTemplateContext();

    context.path = transcoding.getPath();
    context.method = transcoding.getMethod();
    context.selector = transcoding.getSelector();
    context.body = transcoding.getBody();
    context.option = true; // Always true when transcoding is present
    context.responseBody = transcoding.getResponseBody();

    context.additionalBindings = transcoding.getAdditionalBindings().stream()
      .map(TranscodingTemplateContext::fromTranscodingDescriptor)
      .collect(Collectors.toList());

    return context;
  }
}
