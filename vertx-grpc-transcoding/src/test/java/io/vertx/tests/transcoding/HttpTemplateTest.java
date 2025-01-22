package io.vertx.tests.transcoding;

import io.vertx.grpc.transcoding.HttpTemplate;
import io.vertx.grpc.transcoding.HttpTemplateVariable;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class HttpTemplateTest {

  @Test
  public void testParseTest1() {
    HttpTemplate template = HttpTemplate.parse("/shelves/{shelf}/books/{book}");
    assertNotNull(template);
    assertEquals(Arrays.asList("shelves", "*", "books", "*"), template.getSegments());
    List<HttpTemplateVariable> expectedVariables = Arrays.asList(
      HttpTemplateVariable.create(List.of("shelf"), 1, 2, false),
      HttpTemplateVariable.create(List.of("book"), 3, 4, false)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest2() {
    HttpTemplate template = HttpTemplate.parse("/shelves/**");
    assertNotNull(template);
    assertEquals(Arrays.asList("shelves", "**"), template.getSegments());
    assertEquals("", template.getVerb());
    assertEquals(Collections.emptyList(), template.getVariables());
  }

  @Test
  public void testParseTest3a() {
    HttpTemplate template = HttpTemplate.parse("/**");
    assertNotNull(template);
    assertEquals(List.of("**"), template.getSegments());
    assertEquals("", template.getVerb());
    assertEquals(Collections.emptyList(), template.getVariables());
  }

  @Test
  public void testParseTest3b() {
    HttpTemplate template = HttpTemplate.parse("/*");
    assertNotNull(template);
    assertEquals(List.of("*"), template.getSegments());
    assertEquals("", template.getVerb());
    assertEquals(Collections.emptyList(), template.getVariables());
  }

  @Test
  public void testParseTest4a() {
    HttpTemplate template = HttpTemplate.parse("/a:foo");
    assertNotNull(template);
    assertEquals(List.of("a"), template.getSegments());
    assertEquals("foo", template.getVerb());
    assertEquals(Collections.emptyList(), template.getVariables());
  }

  @Test
  public void testParseTest4b() {
    HttpTemplate template = HttpTemplate.parse("/a/b/c:foo");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "b", "c"), template.getSegments());
    assertEquals("foo", template.getVerb());
    assertEquals(Collections.emptyList(), template.getVariables());
  }

  @Test
  public void testParseTest5() {
    HttpTemplate template = HttpTemplate.parse("/*/**");
    assertNotNull(template);
    assertEquals(Arrays.asList("*", "**"), template.getSegments());
    assertEquals("", template.getVerb());
    assertEquals(Collections.emptyList(), template.getVariables());
  }

  @Test
  public void testParseTest6() {
    HttpTemplate template = HttpTemplate.parse("/*/a/**");
    assertNotNull(template);
    assertEquals(Arrays.asList("*", "a", "**"), template.getSegments());
    assertEquals("", template.getVerb());
    assertEquals(Collections.emptyList(), template.getVariables());
  }

  @Test
  public void testParseTest7() {
    HttpTemplate template = HttpTemplate.parse("/a/{a.b.c}");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "*"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("a", "b", "c"), 1, 2, false)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest8() {
    HttpTemplate template = HttpTemplate.parse("/a/{a.b.c=*}");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "*"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("a", "b", "c"), 1, 2, false)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest9() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=*}");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "*"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("b"), 1, 2, false)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest10() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=**}");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "**"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("b"), 1, -1, true)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest11() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=c/*}");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "c", "*"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("b"), 1, 3, false)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest12() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=c/*/d}");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "c", "*", "d"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("b"), 1, 4, false)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest13() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=c/**}");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "c", "**"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("b"), 1, -1, true)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest14() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=c/**}/d/e");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "c", "**", "d", "e"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("b"), 1, -3, true)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest15() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=c/**/d}/e");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "c", "**", "d", "e"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("b"), 1, -2, true)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testParseTest16() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=c/**/d}/e:verb");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "c", "**", "d", "e"), template.getSegments());
    assertEquals("verb", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("b"), 1, -2, true)
    );

    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testCustomVerbTests() {
    HttpTemplate template = HttpTemplate.parse("/*:verb");
    assertNotNull(template);
    assertEquals(List.of("*"), template.getSegments());
    assertEquals(Collections.emptyList(), template.getVariables());

    template = HttpTemplate.parse("/**:verb");
    assertNotNull(template);
    assertEquals(List.of("**"), template.getSegments());
    assertEquals(Collections.emptyList(), template.getVariables());

    template = HttpTemplate.parse("/{a}:verb");
    assertNotNull(template);
    assertEquals(List.of("*"), template.getSegments());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("a"), 0, 1, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/a/b/*:verb");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "b", "*"), template.getSegments());
    assertEquals(Collections.emptyList(), template.getVariables());

    template = HttpTemplate.parse("/a/b/**:verb");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "b", "**"), template.getSegments());
    assertEquals(Collections.emptyList(), template.getVariables());

    template = HttpTemplate.parse("/a/b/{a}:verb");
    assertNotNull(template);
    assertEquals(Arrays.asList("a", "b", "*"), template.getSegments());
    expectedVariables = Collections.singletonList(HttpTemplateVariable.create(List.of("a"), 2, 3, false));
    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testMoreVariableTests() {
    HttpTemplate template = HttpTemplate.parse("/{x}");

    assertNotNull(template);
    assertEquals(List.of("*"), template.getSegments());
    assertEquals("", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x"), 0, 1, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z}");

    assertNotNull(template);
    assertEquals(List.of("*"), template.getSegments());
    assertEquals("", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, 1, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x=*}");

    assertNotNull(template);
    assertEquals(List.of("*"), template.getSegments());
    assertEquals("", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x"), 0, 1, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x=a/*}");

    assertNotNull(template);
    assertEquals(Arrays.asList("a", "*"), template.getSegments());
    assertEquals("", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x"), 0, 2, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z=*/a/b}/c");

    assertNotNull(template);
    assertEquals(Arrays.asList("*", "a", "b", "c"), template.getSegments());
    assertEquals("", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, 3, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x=**}");

    assertNotNull(template);
    assertEquals(List.of("**"), template.getSegments());
    assertEquals("", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x"), 0, -1, true)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z=**}");

    assertNotNull(template);
    assertEquals(List.of("**"), template.getSegments());
    assertEquals("", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, -1, true)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z=a/**/b}");

    assertNotNull(template);
    assertEquals(Arrays.asList("a", "**", "b"), template.getSegments());
    assertEquals("", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, -1, true)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z=a/**/b}/c/d");

    assertNotNull(template);
    assertEquals(Arrays.asList("a", "**", "b", "c", "d"), template.getSegments());
    assertEquals("", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, -3, true)
    );
    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testVariableAndCustomVerbTests() {
    HttpTemplate template = HttpTemplate.parse("/{x}:verb");

    assertNotNull(template);
    assertEquals(List.of("*"), template.getSegments());
    assertEquals("verb", template.getVerb());
    List<HttpTemplateVariable> expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x"), 0, 1, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z}:verb");

    assertNotNull(template);
    assertEquals(List.of("*"), template.getSegments());
    assertEquals("verb", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, 1, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z=*/*}:verb");

    assertNotNull(template);
    assertEquals(Arrays.asList("*", "*"), template.getSegments());
    assertEquals("verb", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, 2, false)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x=**}:myverb");

    assertNotNull(template);
    assertEquals(List.of("**"), template.getSegments());
    assertEquals("myverb", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x"), 0, -1, true)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z=**}:myverb");

    assertNotNull(template);
    assertEquals(List.of("**"), template.getSegments());
    assertEquals("myverb", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, -1, true)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z=a/**/b}:custom");

    assertNotNull(template);
    assertEquals(Arrays.asList("a", "**", "b"), template.getSegments());
    assertEquals("custom", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, -1, true)
    );
    assertVariableList(expectedVariables, template.getVariables());

    template = HttpTemplate.parse("/{x.y.z=a/**/b}/c/d:custom");

    assertNotNull(template);
    assertEquals(Arrays.asList("a", "**", "b", "c", "d"), template.getSegments());
    assertEquals("custom", template.getVerb());
    expectedVariables = Collections.singletonList(
      HttpTemplateVariable.create(List.of("x", "y", "z"), 0, -3, true)
    );
    assertVariableList(expectedVariables, template.getVariables());
  }

  @Test
  public void testRootPath() {
    HttpTemplate template = HttpTemplate.parse("/");
    assertNotNull(template);
    assertEquals(Collections.emptyList(), template.getSegments());
    assertEquals("", template.getVerb());
    assertEquals(Collections.emptyList(), template.getVariables());
  }

  @Test
  public void testErrorTests() {
    assertNull(HttpTemplate.parse(""));
    assertNull(HttpTemplate.parse("//"));
    assertNull(HttpTemplate.parse("/{}"));
    assertNull(HttpTemplate.parse("/a/"));
    assertNull(HttpTemplate.parse("/a//b"));

    assertNull(HttpTemplate.parse(":verb"));
    assertNull(HttpTemplate.parse("/:verb"));
    assertNull(HttpTemplate.parse("/a/:verb"));

    assertNull(HttpTemplate.parse(":"));
    assertNull(HttpTemplate.parse("/:"));
    assertNull(HttpTemplate.parse("/*:"));
    assertNull(HttpTemplate.parse("/**:"));
    assertNull(HttpTemplate.parse("/{var}:"));

    assertNull(HttpTemplate.parse("/a/b/:"));
    assertNull(HttpTemplate.parse("/a/b/*:"));
    assertNull(HttpTemplate.parse("/a/b/**:"));
    assertNull(HttpTemplate.parse("/a/b/{var}:"));

    assertNull(HttpTemplate.parse("/a/{"));
    assertNull(HttpTemplate.parse("/a/{var"));
    assertNull(HttpTemplate.parse("/a/{var."));
    assertNull(HttpTemplate.parse("/a/{x=var:verb}"));

    assertNull(HttpTemplate.parse("a"));
    assertNull(HttpTemplate.parse("{x}"));
    assertNull(HttpTemplate.parse("{x=/a}"));
    assertNull(HttpTemplate.parse("{x=/a/b}"));
    assertNull(HttpTemplate.parse("a/b"));
    assertNull(HttpTemplate.parse("a/b/{x}"));
    assertNull(HttpTemplate.parse("a/{x}/b"));
    assertNull(HttpTemplate.parse("a/{x}/b:verb"));
    assertNull(HttpTemplate.parse("/a/{var=/b}"));
    assertNull(HttpTemplate.parse("/{var=a/{nested=b}}"));

    assertNull(HttpTemplate.parse("/a{x}"));
    assertNull(HttpTemplate.parse("/{x}a"));
    assertNull(HttpTemplate.parse("/a{x}b"));
    assertNull(HttpTemplate.parse("/{x}a{y}"));
    assertNull(HttpTemplate.parse("/a/b{x}"));
    assertNull(HttpTemplate.parse("/a/{x}b"));
    assertNull(HttpTemplate.parse("/a/b{x}c"));
    assertNull(HttpTemplate.parse("/a/{x}b{y}"));
    assertNull(HttpTemplate.parse("/a/b{x}/s"));
    assertNull(HttpTemplate.parse("/a/{x}b/s"));
    assertNull(HttpTemplate.parse("/a/b{x}c/s"));
    assertNull(HttpTemplate.parse("/a/{x}b{y}/s"));
  }

  @Test
  public void testParseVerbTest2() {
    HttpTemplate template = HttpTemplate.parse("/a/*:verb");
    assertNotNull(template);
    assertEquals(template.getSegments(), Arrays.asList("a", "*"));
    assertEquals("verb", template.getVerb());
  }

  @Test
  public void testParseVerbTest3() {
    HttpTemplate template = HttpTemplate.parse("/a/**:verb");
    assertNotNull(template);
    assertEquals(template.getSegments(), Arrays.asList("a", "**"));
    assertEquals("verb", template.getVerb());
  }

  @Test
  public void testParseVerbTest4() {
    HttpTemplate template = HttpTemplate.parse("/a/{b=*}/**:verb");
    assertNotNull(template);
    assertEquals(template.getSegments(), Arrays.asList("a", "*", "**"));
    assertEquals("verb", template.getVerb());
  }

  @Test
  public void testParseNonVerbTest() {
    assertNull(HttpTemplate.parse(":"));
    assertNull(HttpTemplate.parse("/:"));
    assertNull(HttpTemplate.parse("/a/:"));
    assertNull(HttpTemplate.parse("/a/*:"));
    assertNull(HttpTemplate.parse("/a/**:"));
    assertNull(HttpTemplate.parse("/a/{b=*}/**:"));
  }

  private void assertVariableList(List<HttpTemplateVariable> expected, List<HttpTemplateVariable> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      assertVariable(expected.get(i), actual.get(i));
    }
  }

  private void assertVariable(HttpTemplateVariable expected, HttpTemplateVariable actual) {
    assertEquals(expected.getFieldPath(), actual.getFieldPath());
    assertEquals(expected.getStartSegment(), actual.getStartSegment());
    assertEquals(expected.getEndSegment(), actual.getEndSegment());
    assertEquals(expected.hasWildcardPath(), actual.hasWildcardPath());
  }
}
