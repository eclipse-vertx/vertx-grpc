package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.transcoding.impl.config.HttpTemplateParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements a trie-based path matching system for HTTP request routing. This class maintains a tree structure where each node represents a path segment and can match both literal
 * path parts and variable segments including wildcards.
 *
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/path_matcher_node.cc">grpc-httpjson-transcoding</a>
 */
public class PathMatcherNode {

  /** Wildcard string for matching any HTTP method */
  public static final String HTTP_WILD_CARD = "*";

  private final Map<String, PathMatcherNode> children = new HashMap<>();
  private Map<String, PathMatcherNodeLookupResult> results = new HashMap<>();
  private boolean wildcard;

  /**
   * Performs path lookup using depth-first search to find matching handlers. When matching paths, this method follows the Google HTTP Template Spec matching precedence:
   * <ol>
   *  <li>Exact literal matches</li>
   *  <li>Single parameter matches</li>
   *  <li>Wildcard matches</li>
   * </ol>
   *
   * For wildcard nodes, the search continues until either: - A complete match is found - No valid continuation of the path exists in the trie
   *
   * @param path List of path segments to match
   * @param current Current position in the path list being processed
   * @param method HTTP method to match
   * @param result Container for the lookup result
   */
  public void lookupPath(List<String> path, int current, String method, PathMatcherNodeLookupResult result) {
    while (true) {
      if (current == path.size()) {
        if (!getResultForHttpMethod(method, result)) {
          // Check wildcard child for root matches
          PathMatcherNode child = children.get(HttpTemplateParser.WILD_CARD_PATH_KEY);
          if (child != null) {
            child.getResultForHttpMethod(method, result);
          }
        }
        return;
      }
      if (lookupPathFromChild(path.get(current), path, current, method, result)) {
        return;
      }
      if (!wildcard) {
        break;
      }
      current++;
    }

    // Try matching special path parameters in order of precedence
    for (String childKey : new String[] {
      HttpTemplateParser.SINGLE_PARAMETER_KEY,
      HttpTemplateParser.WILD_CARD_PATH_PART_KEY,
      HttpTemplateParser.WILD_CARD_PATH_KEY
    }) {
      if (lookupPathFromChild(childKey, path, current, method, result)) {
        return;
      }
    }
  }

  /**
   * Inserts a new path pattern into the trie.
   *
   * @param info Path information containing the segments to insert
   * @param method HTTP method for this path
   * @param data Handler data to associate with this path
   * @param markDuplicates Whether to mark duplicate registrations
   * @return true if insertion was successful, false if path already exists
   */
  public boolean insertPath(PathInfo info, String method, Object data, boolean markDuplicates) {
    return insertTemplate(info.getPathInfo(), 0, method, data, markDuplicates);
  }

  private boolean insertTemplate(List<String> path, int current, String method, Object data, boolean markDuplicates) {
    if (current == path.size()) {
      PathMatcherNodeLookupResult existing = results.putIfAbsent(method, new PathMatcherNodeLookupResult(data, false));
      if (existing != null) {
        existing.data = data;
        if (markDuplicates) {
          existing.multiple = true;
        }
        return false;
      }
      return true;
    }
    PathMatcherNode child = children.computeIfAbsent(path.get(current), k -> new PathMatcherNode());
    if (path.get(current).equals(HttpTemplateParser.WILD_CARD_PATH_KEY)) {
      child.setWildcard(true);
    }
    return child.insertTemplate(path, current + 1, method, data, markDuplicates);
  }

  private boolean lookupPathFromChild(String key, List<String> path, int current, String method, PathMatcherNodeLookupResult result) {
    PathMatcherNode child = children.get(key);
    if (child != null) {
      child.lookupPath(path, current + 1, method, result);
      if (result != null && result.data != null) {
        return true;
      }
    }
    return false;
  }

  private boolean getResultForHttpMethod(String key, PathMatcherNodeLookupResult result) {
    PathMatcherNodeLookupResult found = results.getOrDefault(key, results.get(HTTP_WILD_CARD));
    if (found != null) {
      result.data = found.data;
      result.multiple = found.multiple;
      return true;
    }
    return false;
  }

  private void setWildcard(boolean wildcard) {
    this.wildcard = wildcard;
  }

  @Override
  public PathMatcherNode clone() {
    PathMatcherNode clone = new PathMatcherNode();
    clone.results = new HashMap<>(this.results);
    for (Map.Entry<String, PathMatcherNode> entry : children.entrySet()) {
      clone.children.put(entry.getKey(), entry.getValue().clone());
    }
    clone.wildcard = this.wildcard;
    return clone;
  }

  /**
   * Container class for path matching results.
   */
  public static class PathMatcherNodeLookupResult {
    private Object data;
    private boolean multiple;

    public PathMatcherNodeLookupResult(Object data, boolean multiple) {
      this.data = data;
      this.multiple = multiple;
    }

    public Object getData() {
      return data;
    }

    public boolean isMultiple() {
      return multiple;
    }
  }

  /**
   * Represents structured path information for registration. Uses the Builder pattern to construct valid path patterns.
   */
  public static class PathInfo {
    private final List<String> pathInfo;

    private PathInfo(Builder builder) {
      this.pathInfo = builder.path;
    }

    public List<String> getPathInfo() {
      return pathInfo;
    }

    /**
     * Builder for constructing PathInfo instances. Supports adding both literal path segments and parameter segments.
     */
    public static class Builder {
      private final List<String> path = new ArrayList<>();

      /**
       * Adds a literal path segment.
       *
       * @param name The literal path segment to add
       * @throws IllegalArgumentException if the segment conflicts with parameter syntax
       */
      public Builder appendLiteralNode(String name) {
        if (name.equals(HttpTemplateParser.SINGLE_PARAMETER_KEY)) {
          throw new IllegalArgumentException("Literal node cannot be a single parameter node");
        }
        path.add(name);
        return this;
      }

      /**
       * Adds a single parameter segment to the path.
       */
      public Builder appendSingleParameterNode() {
        path.add(HttpTemplateParser.SINGLE_PARAMETER_KEY);
        return this;
      }

      /**
       * Constructs the final PathInfo instance.
       */
      public PathInfo build() {
        return new PathInfo(this);
      }
    }
  }
}
