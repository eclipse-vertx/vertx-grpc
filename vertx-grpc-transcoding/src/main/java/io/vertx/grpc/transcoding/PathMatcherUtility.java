package io.vertx.grpc.transcoding;

import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/include/grpc_transcoding/path_matcher.h">grpc-httpjson-transcoding</a>
 */
public class PathMatcherUtility {

  public static boolean registerByHttpRule(PathMatcherBuilder pmb, MethodTranscodingOptions transcodingOptions, String method) {
    return registerByHttpRule(pmb, transcodingOptions, new HashSet<>(), method);
  }

  public static boolean registerByHttpRule(PathMatcherBuilder pmb, MethodTranscodingOptions transcodingOptions, Set<String> systemQueryParameterNames, String method) {
    boolean ok = pmb.register(transcodingOptions, systemQueryParameterNames, method);

    if (transcodingOptions.getAdditionalBindings() == null) {
      return ok;
    }

    for (MethodTranscodingOptions binding : transcodingOptions.getAdditionalBindings()) {
      if (!ok) {
        return ok;
      }
      ok = registerByHttpRule(pmb, binding, systemQueryParameterNames, method);
    }

    return ok;
  }

  protected static List<HttpVariableBinding> extractBindingsFromPath(List<HttpTemplateVariable> vars, List<String> parts, PercentEncoding.UrlUnescapeSpec unescapeSpec) {
    if (vars == null || vars.isEmpty()) {
      return List.of();
    }

    List<HttpVariableBinding> bindings = new ArrayList<>();

    for (HttpTemplateVariable var : vars) {
      HttpVariableBinding binding = new HttpVariableBinding(var.getFieldPath(), null);
      int end = var.getEndSegment() >= 0 ? var.getEndSegment() : parts.size() + var.getEndSegment() + 1;
      boolean multipart = (end - var.getStartSegment()) > 1 || var.getEndSegment() < 0;
      PercentEncoding.UrlUnescapeSpec spec = multipart ? unescapeSpec : PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS;

      for (int i = var.getStartSegment(); i < end; ++i) {
        String currentValue = binding.getValue();
        currentValue = (currentValue == null) ? "" : currentValue;
        binding.setValue(currentValue + PercentEncoding.urlUnescapeString(parts.get(i), spec, false));
        if (i < end - 1) {
          binding.setValue(binding.getValue() + "/");
        }
      }
      bindings.add(binding);
    }

    return bindings;
  }

  protected static List<HttpVariableBinding> extractBindingsFromQueryParameters(String queryParams, Set<String> systemParams, boolean queryParamUnescapePlus) {
    if (queryParams == null) {
      return List.of();
    }

    List<HttpVariableBinding> bindings = new ArrayList<>();
    List<String> params = Splitter.on('&').splitToList(queryParams);

    for (String param : params) {
      int pos = param.indexOf('=');
      if (pos != 0 && pos != -1) {
        String name = param.substring(0, pos);
        if (!systemParams.contains(name)) {
          HttpVariableBinding binding = new HttpVariableBinding(Splitter.on('.').splitToList(name), PercentEncoding.urlUnescapeString(
            param.substring(pos + 1),
            PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS,
            queryParamUnescapePlus));
          bindings.add(binding);
        }
      }
    }

    return bindings;
  }

  protected static List<String> extractRequestParts(String path, Set<String> customVerbs, boolean matchUnregisteredCustomVerb) {
    if (path.indexOf('?') != -1) {
      path = path.substring(0, path.indexOf('?'));
    }

    int lastIndexOfColon = path.lastIndexOf(':');
    int lastIndexOfSlash = path.lastIndexOf('/');
    if (lastIndexOfColon != -1 && lastIndexOfColon > lastIndexOfSlash) {
      String verb = path.substring(lastIndexOfColon + 1);
      if (matchUnregisteredCustomVerb || customVerbs.contains(verb)) {
        path = path.substring(0, lastIndexOfColon);
      }
    }

    List<String> result = new ArrayList<>();
    if (!path.isEmpty()) {
      result = new ArrayList<>(Splitter.on('/').splitToList(path.substring(1)));
    }

    while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
      result.remove(result.size() - 1);
    }

    return result;
  }

  protected static String extractVerb(String path, Set<String> customVerbs, boolean matchUnregisteredCustomVerb) {
    if (path.indexOf('?') != -1) {
      path = path.substring(0, path.indexOf('?'));
    }

    int lastIndexOfColon = path.lastIndexOf(':');
    int lastIndexOfSlash = path.lastIndexOf('/');
    if (lastIndexOfColon != -1 && lastIndexOfColon > lastIndexOfSlash) {
      String verb = path.substring(lastIndexOfColon + 1);
      if (matchUnregisteredCustomVerb || customVerbs.contains(verb)) {
        return verb;
      }
    }

    return "";
  }

  protected static PathMatcherNode.PathMatcherNodeLookupResult lookupInPathMatcherNode(PathMatcherNode root, List<String> parts, String httpMethod) {
    PathMatcherNode.PathMatcherNodeLookupResult result = new PathMatcherNode.PathMatcherNodeLookupResult(null, false);
    root.lookupPath(parts, 0, httpMethod, result);
    return result;
  }

  protected static PathMatcherNode.PathInfo transformHttpTemplate(HttpTemplate template) {
    PathMatcherNode.PathInfo.Builder builder = new PathMatcherNode.PathInfo.Builder();
    for (String part : template.getSegments()) {
      builder.appendLiteralNode(part);
    }
    return builder.build();
  }
}
