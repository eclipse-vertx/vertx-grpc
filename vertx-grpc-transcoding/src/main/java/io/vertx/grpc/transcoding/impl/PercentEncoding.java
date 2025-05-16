package io.vertx.grpc.transcoding.impl;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Utility class for handling percent encoding in URLs using Google Guava. Uses Google Guava's escaping utilities for performance and correctness.
 */
public final class PercentEncoding {

  /**
   * Specifies how to handle reserved characters during URL unescaping.
   */
  public enum UrlUnescapeSpec {
    ALL_CHARACTERS_EXCEPT_RESERVED,
    ALL_CHARACTERS_EXCEPT_SLASH,
    ALL_CHARACTERS
  }

  // RFC 3986 reserved characters: gen-delims and sub-delims
  private static final String RESERVED_CHARS = "!#$&'()*+,/:;=?@[]";
  private static final char[] RESERVED_CHAR_ARRAY;

  private static final BitSet RESERVED_CHAR_SET = new BitSet(128);

  static {
    // Convert reserved chars to array for quick lookup
    RESERVED_CHAR_ARRAY = RESERVED_CHARS.toCharArray();

    // Initialize the BitSet of reserved characters for fast lookup
    for (char c : RESERVED_CHAR_ARRAY) {
      RESERVED_CHAR_SET.set(c);
    }
  }

  private PercentEncoding() {
  }

  /**
   * Unescapes a URL-encoded string according to the specified unescaping rules.
   *
   * @param part The string to unescape
   * @param unescapeSpec The unescaping specification
   * @param unescapePlus Whether to unescape '+' to space
   * @return The unescaped string
   */
  public static String urlUnescapeString(String part, UrlUnescapeSpec unescapeSpec, boolean unescapePlus) {
    if (part == null || part.isEmpty()) {
      return part;
    }

    switch (unescapeSpec) {
      case ALL_CHARACTERS:
        return urlDecode(part, unescapePlus);
      case ALL_CHARACTERS_EXCEPT_SLASH:
        String tempNoSlash = part.replace("%2F", "##SLASH##");
        String decodedNoSlash = urlDecode(tempNoSlash, unescapePlus);
        return decodedNoSlash.replace("##SLASH##", "%2F");
      case ALL_CHARACTERS_EXCEPT_RESERVED:
        return unescapeExceptReserved(part, unescapePlus);
      default:
        return part;
    }
  }

  /**
   * Decodes a URL using URLDecoder with appropriate options.
   */
  private static String urlDecode(String encoded, boolean unescapePlus) {
    if (!unescapePlus && encoded.contains("+")) {
      String temp = encoded.replace("+", "##PLUS##");
      String decoded = URLDecoder.decode(temp, StandardCharsets.UTF_8);
      return decoded.replace("##PLUS##", "+");
    } else {
      return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
  }

  /**
   * Unescapes a URL-encoded string while preserving RFC 3986 reserved characters.
   */
  private static String unescapeExceptReserved(String part, boolean unescapePlus) {
    StringBuilder result = new StringBuilder(part.length());
    char[] chars = part.toCharArray();

    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == '%' && i + 2 < chars.length) {
        // Check if this percent sequence represents a reserved character
        char decoded = decodePercentSequence(chars, i);

        // If it's a reserved character, keep the percent encoding
        if (decoded != 0 && isReservedChar(decoded)) {
          result.append(chars, i, 3);
          i += 2;
        } else {
          // Otherwise, decode it normally with the rest of the string
          String remaining = part.substring(i);
          String decoded_part = urlDecode(remaining, unescapePlus);
          result.append(decoded_part);
          break;
        }
      } else if (chars[i] == '+' && unescapePlus) {
        result.append(' ');
      } else {
        result.append(chars[i]);
      }
    }

    return result.toString();
  }

  /**
   * Decode a percent sequence (%XY) into the character it represents. Returns 0 if the sequence is invalid.
   */
  private static char decodePercentSequence(char[] chars, int start) {
    if (start + 2 >= chars.length || chars[start] != '%') {
      return 0;
    }

    char h1 = chars[start + 1];
    char h2 = chars[start + 2];

    int digit1 = hexDigitToInt(h1);
    int digit2 = hexDigitToInt(h2);

    if (digit1 < 0 || digit2 < 0) {
      return 0;
    }

    return (char) ((digit1 << 4) | digit2);
  }

  /**
   * Convert a hex digit to its integer value. Returns -1 if the character is not a valid hex digit.
   */
  private static int hexDigitToInt(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    } else if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    } else if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    } else {
      return -1;
    }
  }

  /**
   * Check if a character is a reserved character according to RFC 3986.
   */
  private static boolean isReservedChar(char c) {
    return c < 128 && RESERVED_CHAR_SET.get(c);
  }
}
