package io.vertx.tests.transcoding;

import io.vertx.core.http.HttpMethod;
import io.vertx.grpc.transcoding.MethodTranscodingOptions;
import io.vertx.grpc.transcoding.impl.PathMatcher;
import io.vertx.grpc.transcoding.impl.PathMatcherBuilder;
import io.vertx.grpc.transcoding.impl.PathMatcherUtility;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class PathMatcherUtilityTest {

  private final String method1 = "method1";
  private final String method2 = "method2";

  @Test
  public void testNeverRegister() {
    MethodTranscodingOptions options = new MethodTranscodingOptions("selector", HttpMethod.GET, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));
    PathMatcher pm = pmb.build();

    assertNull(pm.lookup("GET", "/any/path"));
  }

  @Test
  public void testRegisterGet() {
    MethodTranscodingOptions options = new MethodTranscodingOptions("selector", HttpMethod.GET, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPut() {
    MethodTranscodingOptions options = new MethodTranscodingOptions("selector", HttpMethod.PUT, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPost() {
    MethodTranscodingOptions options = new MethodTranscodingOptions("selector", HttpMethod.POST, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterDelete() {
    MethodTranscodingOptions options = new MethodTranscodingOptions("selector", HttpMethod.DELETE, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPatch() {
    MethodTranscodingOptions options = new MethodTranscodingOptions("selector", HttpMethod.PATCH, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterCustom() {
    MethodTranscodingOptions options = new MethodTranscodingOptions("selector", HttpMethod.OPTIONS, "/custom_path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterAdditionalBindings() {
    MethodTranscodingOptions options = new MethodTranscodingOptions(
      "selector",
      HttpMethod.GET,
      "/path",
      "body",
      "response",
      Arrays.asList(
        new MethodTranscodingOptions("selector", HttpMethod.OPTIONS, "/custom_path", "body1", "response", null),
        new MethodTranscodingOptions("selector", HttpMethod.HEAD, "/path", null, "response", null),
        new MethodTranscodingOptions("selector", HttpMethod.PUT, "/put_path", null, "response", null)
      )
    );

    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterRootPath() {
    MethodTranscodingOptions options = new MethodTranscodingOptions("selector", HttpMethod.GET, "/", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }
}
