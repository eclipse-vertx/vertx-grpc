package io.vertx.tests.transcoding;

import io.vertx.core.http.HttpMethod;
import io.vertx.grpc.transcoding.PathMatcher;
import io.vertx.grpc.transcoding.PathMatcherBuilder;
import io.vertx.grpc.transcoding.PathMatcherUtility;
import io.vertx.grpc.transcoding.ServiceTranscodingOptions;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class PathMatcherUtilityTest {

  private final String method1 = "method1";
  private final String method2 = "method2";

  @Test
  public void testNeverRegister() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions("selector", HttpMethod.GET, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));
    PathMatcher pm = pmb.build();

    assertNull(pm.lookup("GET", "/any/path"));
  }

  @Test
  public void testRegisterGet() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions("selector", HttpMethod.GET, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPut() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions("selector", HttpMethod.PUT, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPost() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions("selector", HttpMethod.POST, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterDelete() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions("selector", HttpMethod.DELETE, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPatch() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions("selector", HttpMethod.PATCH, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterCustom() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions("selector", HttpMethod.OPTIONS, "/custom_path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterAdditionalBindings() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions(
      "selector",
      HttpMethod.GET,
      "/path",
      "body",
      "response",
      Arrays.asList(
        new ServiceTranscodingOptions("selector", HttpMethod.OPTIONS, "/custom_path", "body1", "response", null),
        new ServiceTranscodingOptions("selector", HttpMethod.HEAD, "/path", null, "response", null),
        new ServiceTranscodingOptions("selector", HttpMethod.PUT, "/put_path", null, "response", null)
      )
    );

    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterRootPath() {
    ServiceTranscodingOptions options = new ServiceTranscodingOptions("selector", HttpMethod.GET, "/", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilder();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }
}
