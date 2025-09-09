package io.vertx.grpc.plugin.generation.context;

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
   * Convert method name to mixed case following JavaBean conventions
   */
  public static String mixedLower(String word) {
    if (word == null || word.isEmpty()) {
      return word;
    }

    StringBuilder result = new StringBuilder();
    result.append(Character.toLowerCase(word.charAt(0)));

    boolean afterUnderscore = false;
    for (int i = 1; i < word.length(); i++) {
      char c = word.charAt(i);

      if (c == '_') {
        afterUnderscore = true;
      } else {
        if (afterUnderscore) {
          result.append(Character.toUpperCase(c));
        } else {
          result.append(c);
        }
        afterUnderscore = false;
      }
    }

    String finalResult = result.toString();
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
