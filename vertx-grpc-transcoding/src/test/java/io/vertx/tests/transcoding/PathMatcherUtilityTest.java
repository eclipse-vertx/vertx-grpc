package io.vertx.tests.transcoding;

import io.vertx.core.http.HttpMethod;
import io.vertx.grpc.transcoding.PathMatcher;
import io.vertx.grpc.transcoding.PathMatcherBuilder;
import io.vertx.grpc.transcoding.ServiceTranscodingOptions;
import io.vertx.grpc.transcoding.impl.PathMatcherBuilderImpl;
import io.vertx.grpc.transcoding.impl.PathMatcherUtility;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class PathMatcherUtilityTest {

  private final String method1 = "method1";
  private final String method2 = "method2";

  @Test
  public void testNeverRegister() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create("selector", HttpMethod.GET, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));
    PathMatcher pm = pmb.build();

    assertNull(pm.lookup("GET", "/any/path"));
  }

  @Test
  public void testRegisterGet() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create("selector", HttpMethod.GET, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPut() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create("selector", HttpMethod.PUT, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPost() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create("selector", HttpMethod.POST, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterDelete() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create("selector", HttpMethod.DELETE, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterPatch() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create("selector", HttpMethod.PATCH, "/path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterCustom() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create("selector", HttpMethod.OPTIONS, "/custom_path", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterAdditionalBindings() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create(
      "selector",
      HttpMethod.GET,
      "/path",
      "body",
      "response",
      Arrays.asList(
        ServiceTranscodingOptions.createBinding("selector", HttpMethod.OPTIONS, "/custom_path", "body1", "response"),
        ServiceTranscodingOptions.createBinding("selector", HttpMethod.HEAD, "/path", null, "response"),
        ServiceTranscodingOptions.createBinding("selector", HttpMethod.PUT, "/put_path", null, "response")
      )
    );

    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }

  @Test
  public void testRegisterRootPath() {
    ServiceTranscodingOptions options = ServiceTranscodingOptions.create("selector", HttpMethod.GET, "/", "body", "response", Collections.emptyList());
    PathMatcherBuilder pmb = new PathMatcherBuilderImpl();

    assertTrue(PathMatcherUtility.registerByHttpRule(pmb, options, method1));

    Set<String> queryParams = new HashSet<>(List.of("key"));
    assertFalse(PathMatcherUtility.registerByHttpRule(pmb, options, queryParams, method2));
  }
}
