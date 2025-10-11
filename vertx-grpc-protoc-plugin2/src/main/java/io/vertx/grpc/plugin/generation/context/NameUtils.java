package io.vertx.grpc.plugin.generation.context;

import io.vertx.codegen.format.LowerCamelCase;

import java.util.Arrays;
import java.util.List;

public class NameUtils {

  private static final List<String> JAVA_KEYWORDS = Arrays.asList(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new", "package",
    "private", "protected", "public", "return", "short", "static", "strictfp",
    "super", "switch", "synchronized", "this", "throw", "throws", "transient",
    "try", "void", "volatile", "while", "true", "false"
  );

  /**
   * Formats a given word into a valid method name using lower camel case. If the resulting formatted name matches a Java keyword, an underscore is appended to the name to make it
   * valid.
   *
   * @param word the input string to format as a method name; may be null or empty
   * @return the formatted method name in lower camel case, or the original string if it is null or empty
   */
  public static String formatMethodName(String word) {
    if (word == null || word.isEmpty()) {
      return word;
    }

    String finalResult = LowerCamelCase.INSTANCE.format(LowerCamelCase.INSTANCE.parse(word));

    if (JAVA_KEYWORDS.contains(finalResult)) {
      finalResult += "_";
    }

    return finalResult;
  }

  /**
   * Convert camelCase to UPPER_UNDERSCORE format
   */
  public static String toUpperUnderscore(String methodName) {
    if (methodName == null || methodName.isEmpty()) {
      return methodName;
    }

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < methodName.length(); i++) {
      char c = methodName.charAt(i);
      result.append(Character.toUpperCase(c));

      if (i < methodName.length() - 1 && Character.isLowerCase(c) && Character.isUpperCase(methodName.charAt(i + 1))) {
        result.append('_');
      }
    }

    return result.toString();
  }

  /**
   * Convert package name to file path
   */
  public static String packageToPath(String packageName) {
    return packageName == null ? "" : packageName.replace('.', '/');
  }
}
