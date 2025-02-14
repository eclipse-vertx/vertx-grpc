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
    MethodTranscodingOptions options = ServerTranscodingTest.create("selector", HttpMethod.GET, "/path", "body", "response");
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));
    PathMatcher pm = pmb.build();

    assertNull(pm.lookup("GET", "/any/path"));
  }

  @Test
  public void testRegisterGet() {
    MethodTranscodingOptions options = ServerTranscodingTest.create("selector", HttpMethod.GET, "/path", "body", "response");
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPut() {
    MethodTranscodingOptions options = ServerTranscodingTest.create("selector", HttpMethod.PUT, "/path", "body", "response");
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPost() {
    MethodTranscodingOptions options = ServerTranscodingTest.create("selector", HttpMethod.POST, "/path", "body", "response");
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterDelete() {
    MethodTranscodingOptions options = ServerTranscodingTest.create("selector", HttpMethod.DELETE, "/path", "body", "response");
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPatch() {
    MethodTranscodingOptions options = ServerTranscodingTest.create("selector", HttpMethod.PATCH, "/path", "body", "response");
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterCustom() {
    MethodTranscodingOptions options = ServerTranscodingTest.create("selector", HttpMethod.OPTIONS, "/custom_path", "body", "response");
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterAdditionalBindings() {
    MethodTranscodingOptions options = ServerTranscodingTest.create(
      "selector",
      HttpMethod.GET,
      "/path",
      "body",
      "response",
         ServerTranscodingTest.create("selector", HttpMethod.OPTIONS, "/custom_path", "body1", "response"),
         ServerTranscodingTest.create("selector", HttpMethod.HEAD, "/path", null, "response"),
         ServerTranscodingTest.create("selector", HttpMethod.PUT, "/put_path", null, "response")
    );

    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterRootPath() {
    MethodTranscodingOptions options = ServerTranscodingTest.create("selector", HttpMethod.GET, "/", "body", "response");
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }
}
