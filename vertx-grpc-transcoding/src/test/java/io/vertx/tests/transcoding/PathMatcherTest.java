package io.vertx.tests.transcoding;

import io.vertx.core.http.HttpMethod;
import io.vertx.grpc.transcoding.MethodTranscodingOptions;
import io.vertx.grpc.transcoding.impl.PathMatcher;
import io.vertx.grpc.transcoding.impl.PathMatcherBuilder;
import io.vertx.grpc.transcoding.impl.PathMatcherLookupResult;
import io.vertx.grpc.transcoding.impl.PercentEncoding;
import io.vertx.grpc.transcoding.impl.config.HttpVariableBinding;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class PathMatcherTest {

  private final PathMatcherBuilder builder = new PathMatcherBuilder();
  private PathMatcher matcher;
  private final List<String> storedMethods = new ArrayList<>();

  private String addPathWithBodyFieldPath(String httpMethod, String httpTemplate, String bodyFieldPath) {
    String method = "method_" + storedMethods.size();
    MethodTranscodingOptions transcodingOptions = ServerTranscodingTest.create("selector", HttpMethod.valueOf(httpMethod), httpTemplate, bodyFieldPath, "response");
    assertTrue(builder.register(transcodingOptions, method));
    storedMethods.add(method);
    return method;
  }

  private String addPathWithSystemParams(String httpMethod, String httpTemplate, Set<String> systemParams) {
    String method = "method_" + storedMethods.size();
    MethodTranscodingOptions transcodingOptions = ServerTranscodingTest.create("selector", HttpMethod.valueOf(httpMethod), httpTemplate, "", "response");
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

  private PathMatcherLookupResult lookup(String method, String path) {
    return matcher.lookup(method, path, "");
  }

  private PathMatcherLookupResult lookupWithParams(String method, String path, String queryParams) {
    return matcher.lookup(method, path, queryParams);
  }

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

  @Test
  public void testVariableBindingsWithCustomVerb() {
    String verbY = addGetPath("/a/{y=*}:verb");
    String verbYD = addGetPath("/a/{y=d/**}:verb");
    String verbA = addGetPath("/{x=*}/a:verb");
    String verbB = addGetPath("/{x=**}/b:verb");
    String verbXF = addGetPath("/e/{x=*}/f:verb");
    String verbXH = addGetPath("/g/{x=**}/h:verb");

    build();

    PathMatcherLookupResult result = lookup("GET", "/a/world:verb");
    assertEquals(verbY, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("y"), "world")), result.getVariableBindings());

    result = lookup("GET", "/a/d/animal/tiger:verb");
    assertEquals(verbYD, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("y"), "d/animal/tiger")), result.getVariableBindings());

    result = lookup("GET", "/foo/a:verb");
    assertEquals(verbA, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "foo")), result.getVariableBindings());

    result = lookup("GET", "/foo/bar/baz/b:verb");
    assertEquals(verbB, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "foo/bar/baz")), result.getVariableBindings());

    result = lookup("GET", "/e/foo/f:verb");
    assertEquals(verbXF, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "foo")), result.getVariableBindings());

    result = lookup("GET", "/g/foo/bar/h:verb");
    assertEquals(verbXH, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "foo/bar")), result.getVariableBindings());
  }

  @Test
  public void testConstantSuffixesWithVariable() {
    String ab = addGetPath("/a/{x=b/**}");
    String abz = addGetPath("/a/{x=b/**}/z");
    String abyz = addGetPath("/a/{x=b/**}/y/z");
    String verbAB = addGetPath("/a/{x=b/**}:verb");
    String a = addGetPath("/a/{x=**}");
    String cde = addGetPath("/c/{x=*}/{y=d/**}/e");
    String verbCDE = addGetPath("/c/{x=*}/{y=d/**}/e:verb");
    String fg = addGetPath("/f/{x=*}/{y=**}/g");
    String verbFG = addGetPath("/f/{x=*}/{y=**}/g:verb");
    String fooABYZ = addGetPath("/a/{x=b/*/y/z/**}/foo");
    String fooXABYZ = addGetPath("/a/{x=b/*/**/y/z}/foo");

    build();

    PathMatcherLookupResult result = lookup("GET", "/a/b/hello/world/c");
    assertEquals(ab, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "b/hello/world/c")), result.getVariableBindings());

    result = lookup("GET", "/a/b/world/c/z");
    assertEquals(abz, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "b/world/c")), result.getVariableBindings());

    result = lookup("GET", "/a/b/world/c/y/z");
    assertEquals(abyz, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "b/world/c")), result.getVariableBindings());

    result = lookup("GET", "/a/b/world/c:verb");
    assertEquals(verbAB, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "b/world/c")), result.getVariableBindings());

    result = lookup("GET", "/a/hello/b/world/c");
    assertEquals(a, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "hello/b/world/c")), result.getVariableBindings());

    result = lookup("GET", "/c/hello/d/esp/world/e");
    assertEquals(cde, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "hello"),
        new HttpVariableBinding(Collections.singletonList("y"), "d/esp/world")),
      result.getVariableBindings());

    result = lookup("GET", "/c/hola/d/esp/mundo/e:verb");
    assertEquals(verbCDE, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "hola"),
        new HttpVariableBinding(Collections.singletonList("y"), "d/esp/mundo")),
      result.getVariableBindings());

    result = lookup("GET", "/f/foo/bar/baz/g");
    assertEquals(fg, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "foo"),
        new HttpVariableBinding(Collections.singletonList("y"), "bar/baz")),
      result.getVariableBindings());

    result = lookup("GET", "/f/foo/bar/baz/g:verb");
    assertEquals(verbFG, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "foo"),
        new HttpVariableBinding(Collections.singletonList("y"), "bar/baz")),
      result.getVariableBindings());

    result = lookup("GET", "/a/b/foo/y/z/bar/baz/foo");
    assertEquals(fooABYZ, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "b/foo/y/z/bar/baz")), result.getVariableBindings());

    result = lookup("GET", "/a/b/foo/bar/baz/y/z/foo");
    assertEquals(fooXABYZ, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "b/foo/bar/baz/y/z")), result.getVariableBindings());
  }

  @Test
  public void testCustomVerbMatches() {
    String someConstVerb = addGetPath("/some/const:verb");
    String someVerb = addGetPath("/some/*:verb");
    String someFooVerb = addGetPath("/some/*/foo:verb");
    String otherVerb = addGetPath("/other/**:verb");
    String otherConstVerb = addGetPath("/other/**/const:verb");

    build();

    assertNotNull(someConstVerb);
    assertNotNull(someVerb);
    assertNotNull(someFooVerb);
    assertNotNull(otherVerb);
    assertNotNull(otherConstVerb);

    assertEquals(someConstVerb, lookupNoBindings("GET", "/some/const:verb"));
    assertEquals(someVerb, lookupNoBindings("GET", "/some/other:verb"));
    assertNull(lookupNoBindings("GET", "/some/other:verb/"));
    assertEquals(someFooVerb, lookupNoBindings("GET", "/some/bar/foo:verb"));
    assertNull(lookupNoBindings("GET", "/some/foo1/foo2/foo:verb"));
    assertNull(lookupNoBindings("GET", "/some/foo/bar:verb"));
    assertEquals(otherVerb, lookupNoBindings("GET", "/other/bar/foo:verb"));
    assertEquals(otherConstVerb, lookupNoBindings("GET", "/other/bar/foo/const:verb"));
  }

  @Test
  public void testCustomVerbMatch2() {
    String verb = addGetPath("/{a=*}/{b=*}:verb");

    build();

    PathMatcherLookupResult result = lookup("GET", "/some:verb/const:verb");
    assertEquals(verb, result.getMethod());
    assertEquals(2, result.getVariableBindings().size());
    assertEquals("some:verb", result.getVariableBindings().get(0).getValue());
    assertEquals("const", result.getVariableBindings().get(1).getValue());
  }

  @Test
  public void testCustomVerbMatch3() {
    String verb = addGetPath("/foo/{a=*}");

    build();

    // This is not custom verb since it was not configured
    PathMatcherLookupResult result = lookup("GET", "/foo/other:verb");
    assertEquals(verb, result.getMethod());
    assertEquals(1, result.getVariableBindings().size());
    assertEquals("other:verb", result.getVariableBindings().get(0).getValue());
  }

  @Test
  public void testCustomVerbMatch4() {
    String a = addGetPath("/foo/*/hello");

    build();

    assertNotNull(a);

    // last slash is before last colon
    assertEquals(a, lookupNoBindings("GET", "/foo/other:verb/hello"));
  }

  @Test
  public void testCustomVerbMatch5() {
    String verb = addGetPath("/{a=**}:verb");
    String nonVerb = addGetPath("/{a=**}");

    build();

    PathMatcherLookupResult result = lookup("GET", "/some:verb/const:verb");
    assertEquals(verb, result.getMethod());
    assertEquals(1, result.getVariableBindings().size());
    assertEquals("some:verb/const", result.getVariableBindings().get(0).getValue());

    result = lookup("GET", "/some:verb/const");
    assertEquals(nonVerb, result.getMethod());
    assertEquals(1, result.getVariableBindings().size());
    assertEquals("some:verb/const", result.getVariableBindings().get(0).getValue());

    result = lookup("GET", "/some:verb2/const:verb2");
    assertEquals(nonVerb, result.getMethod());
    assertEquals(1, result.getVariableBindings().size());
    assertEquals("some:verb2/const:verb2", result.getVariableBindings().get(0).getValue());
  }

  @Test
  public void testRejectPartialMatches() {
    String prefix_middle_suffix = addGetPath("/prefix/middle/suffix");
    String prefix_middle = addGetPath("/prefix/middle");
    String prefix = addGetPath("/prefix");

    build();

    assertNotNull(prefix_middle_suffix);
    assertNotNull(prefix_middle);
    assertNotNull(prefix);

    assertEquals(prefix_middle_suffix, lookupNoBindings("GET", "/prefix/middle/suffix"));
    assertEquals(prefix_middle, lookupNoBindings("GET", "/prefix/middle"));
    assertEquals(prefix, lookupNoBindings("GET", "/prefix"));

    assertNull(lookupNoBindings("GET", "/prefix/middle/suffix/other"));
    assertNull(lookupNoBindings("GET", "/prefix/middle/other"));
    assertNull(lookupNoBindings("GET", "/prefix/other"));
    assertNull(lookupNoBindings("GET", "/other"));
  }

  @Test
  public void testLookupReturnsNullIfMatcherEmpty() {
    build();
    assertNull(lookupNoBindings("GET", "a/b/blue/foo"));
  }

  @Test
  public void testLookupSimplePaths() {
    String pms = addGetPath("/prefix/middle/suffix");
    String pmo = addGetPath("/prefix/middle/othersuffix");
    String pos = addGetPath("/prefix/othermiddle/suffix");
    String oms = addGetPath("/otherprefix/middle/suffix");
    String os = addGetPath("/otherprefix/suffix");

    build();

    assertNotNull(pms);
    assertNotNull(pmo);
    assertNotNull(pos);
    assertNotNull(oms);
    assertNotNull(os);

    assertNull(lookupNoBindings("GET", "/prefix/not/a/path"));
    assertNull(lookupNoBindings("GET", "/prefix/middle"));
    assertNull(lookupNoBindings("GET", "/prefix/not/othermiddle"));
    assertNull(lookupNoBindings("GET", "/otherprefix/suffix/othermiddle"));

    assertEquals(pms, lookupNoBindings("GET", "/prefix/middle/suffix"));
    assertEquals(pmo, lookupNoBindings("GET", "/prefix/middle/othersuffix"));
    assertEquals(pos, lookupNoBindings("GET", "/prefix/othermiddle/suffix"));
    assertEquals(oms, lookupNoBindings("GET", "/otherprefix/middle/suffix"));
    assertEquals(os, lookupNoBindings("GET", "/otherprefix/suffix"));
    assertEquals(os, lookupNoBindings("GET", "/otherprefix/suffix?foo=bar"));
  }

  @Test
  public void testLookupReturnsNullForOverspecifiedPath() {
    assertNotNull(addGetPath("/a/b/c"));
    assertNotNull(addGetPath("/a/b"));

    build();

    assertNull(lookupNoBindings("GET", "/a/b/c/d"));
  }

  @Test
  public void testReturnNullForUnderspecifiedPath() {
    assertNotNull(addGetPath("/a/b/c/d"));

    build();

    assertNull(lookupNoBindings("GET", "/a/b/c"));
  }

  @Test
  public void testDifferentHttpMethod() {
    String ab = addGetPath("/a/b");

    build();

    assertNotNull(ab);
    assertEquals(ab, lookupNoBindings("GET", "/a/b"));
    assertNull(lookupNoBindings("POST", "/a/b"));
  }

  @Test
  public void testVariableBindingsWithQueryParams() {
    String a = addGetPath("/a");
    String ab = addGetPath("/a/{x}/b");
    String abc = addGetPath("/a/{x}/b/{y}/c");

    build();

    PathMatcherLookupResult result = lookupWithParams("GET", "/a", "x=hello");
    assertEquals(a, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "hello")), result.getVariableBindings());

    result = lookupWithParams("GET", "/a/book/b", "y=shelf&z=author");
    assertEquals(ab, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "book"),
        new HttpVariableBinding(Collections.singletonList("y"), "shelf"),
        new HttpVariableBinding(Collections.singletonList("z"), "author")),
      result.getVariableBindings());

    result = lookupWithParams("GET", "/a/hello/b/endpoints/c", "z=server&t=proxy");
    assertEquals(abc, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "hello"),
        new HttpVariableBinding(Collections.singletonList("y"), "endpoints"),
        new HttpVariableBinding(Collections.singletonList("z"), "server"),
        new HttpVariableBinding(Collections.singletonList("t"), "proxy")),
      result.getVariableBindings());
  }

  @Test
  public void testVariableBindingsWithQueryParamsEncoding() {
    String a = addGetPath("/a");

    build();

    PathMatcherLookupResult result = lookupWithParams("GET", "/a", "x=Hello%20world");
    assertEquals(a, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "Hello world")), result.getVariableBindings());

    result = lookupWithParams("GET", "/a", "x=%24%25%2F%20%0A");

    assertEquals(a, result.getMethod());
    assertVariableList(Collections.singletonList(new HttpVariableBinding(Collections.singletonList("x"), "$%/ \n")), result.getVariableBindings());
  }

  @Test
  public void testQueryParameterNotUnescapePlus() {
    String a = addGetPath("/a");

    build();

    PathMatcherLookupResult result = lookupWithParams("GET", "/a", "x=Hello+world&y=%2B+%20");
    assertEquals(a, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "Hello+world"),
        new HttpVariableBinding(Collections.singletonList("y"), "++ ")),
      result.getVariableBindings());
  }

  @Test
  public void testVariableBindingsWithQueryParamsAndSystemParams() {
    Set<String> systemParams = new HashSet<>(Arrays.asList("key", "api_key"));
    String ab = addPathWithSystemParams("GET", "/a/{x}/b", systemParams);

    build();

    assertNotNull(ab);

    PathMatcherLookupResult result = lookupWithParams("GET", "/a/hello/b", "y=world&api_key=secret");
    assertEquals(ab, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "hello"),
        new HttpVariableBinding(Collections.singletonList("y"), "world")),
      result.getVariableBindings());

    result = lookupWithParams("GET", "/a/hello/b", "key=secret&y=world");
    assertEquals(ab, result.getMethod());
    assertVariableList(Arrays.asList(
        new HttpVariableBinding(Collections.singletonList("x"), "hello"),
        new HttpVariableBinding(Collections.singletonList("y"), "world")),
      result.getVariableBindings());
  }

  @Test
  public void testWildCardMatchesManyWithoutStackOverflow() {
    String a = addGetPath("/a/**/x");

    build();

    assertNotNull(a);

    String lotsOfSlashes = "/".repeat(6400);

    assertEquals(a, lookupNoBindings("GET", "/a" + lotsOfSlashes + "x"));
    assertNull(lookupNoBindings("GET", "/a" + lotsOfSlashes + "y"));
  }
}
