package io.vertx.tests.transcoding;

import io.vertx.core.http.HttpMethod;
import io.vertx.grpc.transcoding.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class PathMatcherTest {

  private final PathMatcherBuilder builder = new PathMatcherBuilder();
  private PathMatcher matcher;
  private final List<String> storedMethods = new ArrayList<>();

  private String addPathWithBodyFieldPath(String httpMethod, String httpTemplate, String bodyFieldPath) {
    String method = "method_" + storedMethods.size();
    MethodTranscodingOptions transcodingOptions = new MethodTranscodingOptions("selector", HttpMethod.valueOf(httpMethod), httpTemplate, bodyFieldPath, "response", List.of());
    assertTrue(builder.register(transcodingOptions, method));
    storedMethods.add(method);
    return method;
  }

  private String addPathWithSystemParams(String httpMethod, String httpTemplate, Set<String> systemParams) {
    String method = "method_" + storedMethods.size();
    MethodTranscodingOptions transcodingOptions = new MethodTranscodingOptions("selector", HttpMethod.valueOf(httpMethod), httpTemplate, "", "response", List.of());
    assertTrue(builder.register(transcodingOptions, systemParams, method));
    storedMethods.add(method);
    return method;
  }

  private String addPath(String httpMethod, String httpTemplate) {
    return addPathWithBodyFieldPath(httpMethod, httpTemplate, "");
  }

  private String addGetPath(String path) {
    return addPath("GET", path);
  }

  private void build() {
    matcher = builder.build();
  }

  /*private String lookupWithBodyFieldPath(String method, String path, List<HttpVariableBinding> bindings, String[] bodyFieldPath) {
    return matcher.lookup(method, path, "", bindings, bodyFieldPath);
  }*/

  private PathMatcherLookupResult lookup(String method, String path) {
    return matcher.lookup(method, path, "");
  }

  /*private String lookupWithParams(String method, String path, String queryParams,
    List<HttpVariableBinding> bindings) {
    String[] bodyFieldPath = new String[1];
    return matcher.lookup(method, path, queryParams, bindings, bodyFieldPath);
  }*/

  private String lookupNoBindings(String method, String path) {
    return matcher.lookup(method, path);
  }

  private void assertVariableList(List<HttpVariableBinding> expected, List<HttpVariableBinding> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      assertVariable(expected.get(i), actual.get(i));
    }
  }

  private void assertVariable(HttpVariableBinding expected, HttpVariableBinding actual) {
    assertEquals(expected.getFieldPath(), actual.getFieldPath());
    assertEquals(expected.getValue(), actual.getValue());
  }

  private void multiSegmentMatchWithReservedCharactersBase(String expectedComponent) {
    String path = addGetPath("/a/{x=*}/{y=**}/c");

    build();

    PathMatcherLookupResult result = lookup("GET", "/a/%21%23%24%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D/%21%23%24%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D/c");

    assertEquals(path, result.getMethod());
    assertVariableList(
      Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "!#$&'()*+,/:;=?@[]"),
        new HttpVariableBinding(Collections.singletonList("y"), expectedComponent)
      ), result.getVariableBindings());
  }

  @Test
  public void testWildCardMatchesRoot() {
    String data = addGetPath("/**");

    build();

    assertEquals(data, lookupNoBindings("GET", "/"));
    assertEquals(data, lookupNoBindings("GET", "/a"));
    assertEquals(data, lookupNoBindings("GET", "/a/"));
  }

  @Test
  public void testWildCardMatches() {
    // '*' only matches one path segment, but '**' matches the remaining path.
    String pathA = addGetPath("/a/**");
    String pathB = addGetPath("/b/*");
    String pathCD = addGetPath("/c/*/d/**");
    String pathCDE = addGetPath("/c/*/d/e");
    String pathCFDE = addGetPath("/c/f/d/e");
    String root = addGetPath("/");

    build();

    assertEquals(pathA, lookupNoBindings("GET", "/a/b"));
    assertEquals(pathA, lookupNoBindings("GET", "/a/b/c"));
    assertEquals(pathB, lookupNoBindings("GET", "/b/c"));

    assertNull(lookupNoBindings("GET", "b/c/d"));
    assertEquals(pathCD, lookupNoBindings("GET", "/c/u/d/v"));
    assertEquals(pathCD, lookupNoBindings("GET", "/c/v/d/w/x"));
    assertNull(lookupNoBindings("GET", "/c/x/y/d/z"));
    assertNull(lookupNoBindings("GET", "/c//v/d/w/x"));

    // Test that more specific match overrides wildcard "**"" match.
    assertEquals(pathCDE, lookupNoBindings("GET", "/c/x/d/e"));
    // Test that more specific match overrides wildcard "*"" match.
    assertEquals(pathCFDE, lookupNoBindings("GET", "/c/f/d/e"));

    assertEquals(root, lookupNoBindings("GET", "/"));
  }

  @Test
  public void testVariableBindings() {
    String pathACDE = addGetPath("/a/{x}/c/d/e");
    String pathABC = addGetPath("/{x=a/*}/b/{y=*}/c");
    String pathABD = addGetPath("/a/{x=b/*}/{y=d/**}");
    String pathAlphaBetaGamma = addGetPath("/alpha/{x=*}/beta/{y=**}/gamma");
    String pathA = addGetPath("/{x=*}/a");
    String pathAB = addGetPath("/{x=**}/a/b");
    String pathAB0 = addGetPath("/a/b/{x=*}");
    String pathABC0 = addGetPath("/a/b/c/{x=**}");
    String pathDEF = addGetPath("/{x=*}/d/e/f/{y=**}");

    build();

    PathMatcherLookupResult result = lookup("GET", "/a/book/c/d/e");

    assertEquals(pathACDE, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "book")), result.getVariableBindings());

    result = lookup("GET", "/a/hello/b/world/c");

    assertEquals(pathABC, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "a/hello"),
        new HttpVariableBinding(Collections.singletonList("y"), "world")),
      result.getVariableBindings());

    result = lookup("GET", "/a/b/zoo/d/animal/tiger");

    assertEquals(pathABD, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "b/zoo"),
        new HttpVariableBinding(Collections.singletonList("y"), "d/animal/tiger")),
      result.getVariableBindings());

    result = lookup("GET", "/alpha/dog/beta/eat/bones/gamma");

    assertEquals(pathAlphaBetaGamma, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "dog"),
        new HttpVariableBinding(Collections.singletonList("y"), "eat/bones")),
      result.getVariableBindings());

    result = lookup("GET", "/foo/a");

    assertEquals(pathA, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "foo")), result.getVariableBindings());

    result = lookup("GET", "/foo/bar/a/b");

    assertEquals(pathAB, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "foo/bar")), result.getVariableBindings());

    result = lookup("GET", "/a/b/foo");

    assertEquals(pathAB0, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "foo")), result.getVariableBindings());

    result = lookup("GET", "/a/b/c/foo/bar/baz");

    assertEquals(pathABC0, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "foo/bar/baz")), result.getVariableBindings());

    result = lookup("GET", "/foo/d/e/f/bar/baz");

    assertEquals(pathDEF, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "foo"),
        new HttpVariableBinding(Collections.singletonList("y"), "bar/baz")),
      result.getVariableBindings());
  }

  @Test
  public void testPercentEscapesUnescapedForSingleSegment() {
    String path = addGetPath("/a/{x}/c");

    build();

    PathMatcherLookupResult result = lookup("GET", "/a/p%20q%2Fr+/c");
    // Also test '+',  make sure it is not unescaped
    assertEquals(path, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "p q/r+")), result.getVariableBindings());
  }

  @Test
  public void testPercentEscapesUnescapedForSingleSegmentAllAsciiChars() {
    String getPath = addGetPath("/{x}");
    build();

    for (int u = 0; u < 2; ++u) {
      for (char c = 0; c < 0x7f; ++c) {
        String path = "/%" + String.format("%02x", (int) c);

        PathMatcherLookupResult result = lookup("GET", path);
        assertEquals(getPath, result.getMethod());
        assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), String.valueOf(c))), result.getVariableBindings());
      }
    }
  }

  @Test
  public void testPercentEscapesNotUnescapedForMultiSegment1() {
    String path = addGetPath("/a/{x=p/*/q/*}/c");
    build();

    PathMatcherLookupResult result = lookup("GET", "/a/p/foo%20foo/q/bar%2Fbar/c");
    assertEquals(path, result.getMethod());
    // space (%20) is escaped, but slash (%2F) isn't.
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "p/foo foo/q/bar%2Fbar")), result.getVariableBindings());
  }

  @Test
  public void testPercentEscapesNotUnescapedForMultiSegment2() {
    String path = addGetPath("/a/{x=**}/c");
    build();

    PathMatcherLookupResult result = lookup("GET", "/a/p/foo%20foo/q/bar%2Fbar+/c");
    // Also test '+',  make sure it is not unescaped
    assertEquals(path, result.getMethod());
    // space (%20) is unescaped, but slash (%2F) isn't. nor +
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "p/foo foo/q/bar%2Fbar+")), result.getVariableBindings());
  }

  @Test
  public void testOnlyUnreservedCharsAreUnescapedForMultiSegmentMatchUnescapeAllExceptReservedImplicit() {
    multiSegmentMatchWithReservedCharactersBase("%21%23%24%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D");
  }

  @Test
  public void testOnlyUnreservedCharsAreUnescapedForMultiSegmentMatchUnescapeAllExceptReservedExplicit() {
    builder.setUrlUnescapeSpec(PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_RESERVED);
    multiSegmentMatchWithReservedCharactersBase("%21%23%24%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D");
  }

  @Test
  public void testOnlyUnreservedCharsAreUnescapedForMultiSegmentMatchUnescapeAllExceptSlash() {
    builder.setUrlUnescapeSpec(PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_SLASH);
    multiSegmentMatchWithReservedCharactersBase("!#$&'()*+,%2F:;=?@[]");
  }

  @Test
  public void testOnlyUnreservedCharsAreUnescapedForMultiSegmentMatchUnescapeAll() {
    builder.setUrlUnescapeSpec(PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS);
    multiSegmentMatchWithReservedCharactersBase("!#$&'()*+,/:;=?@[]");
  }

  @Test
  public void testCustomVerbIssue() {
    String listPerson = addGetPath("/person");
    String getPerson = addGetPath("/person/{id=*}");
    String verb = addGetPath("/{x=**}:verb");

    build();

    PathMatcherLookupResult result = lookup("GET", "/person:verb");

    // with the verb
    assertEquals(verb, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "person")), result.getVariableBindings());

    result = lookup("GET", "/person/jason:verb");

    assertEquals(verb, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "person/jason")), result.getVariableBindings());

    // with the verb but with a different prefix
    result = lookup("GET", "/animal:verb");

    assertEquals(verb, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "animal")), result.getVariableBindings());

    result = lookup("GET", "/animal/cat:verb");

    assertEquals(verb, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "animal/cat")), result.getVariableBindings());

    // without a verb
    assertEquals(listPerson, lookup("GET", "/person").getMethod());
    assertEquals(getPerson, lookup("GET", "/person/jason").getMethod());
    assertNull(lookup("GET", "/animal"));
    assertNull(lookup("GET", "/animal/cat"));

    // with a non-verb
    assertNull(lookup("GET", "/person:other"));

    result = lookup("GET", "/person/jason:other");

    assertEquals(getPerson, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding((Collections.singletonList("id")), "jason:other")), result.getVariableBindings());

    assertNull(lookup("GET", "/animal:other"));
    assertNull(lookup("GET", "/animal/cat:other"));
  }
}
