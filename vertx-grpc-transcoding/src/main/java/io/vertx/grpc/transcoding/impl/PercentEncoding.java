package io.vertx.grpc.transcoding.impl;

import java.util.BitSet;

/**
 * Highly optimized utility class for handling percent encoding in URLs.
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

  // RFC 3986/RFC 6570 reserved characters: gen-delims and sub-delims
  // https://datatracker.ietf.org/doc/html/rfc3986#section-2.2
  // https://datatracker.ietf.org/doc/html/rfc6570#section-2.2
  private static final BitSet RESERVED_CHAR_SET = new BitSet(128);
  private static final BitSet HEX_DIGITS = new BitSet(128);

  // Precomputed hex values
  private static final int[] HEX_VALUES = new int[128];

  static {
    // Initialize the BitSet of reserved characters
    for (char c : "!#$&'()*+,/:;=?@[]".toCharArray()) {
      RESERVED_CHAR_SET.set(c);
    }

    // Initialize hex digits lookup
    for (char c = '0'; c <= '9'; c++) {
      HEX_DIGITS.set(c);
      HEX_VALUES[c] = c - '0';
    }
    for (char c = 'A'; c <= 'F'; c++) {
      HEX_DIGITS.set(c);
      HEX_VALUES[c] = c - 'A' + 10;
    }
    for (char c = 'a'; c <= 'f'; c++) {
      HEX_DIGITS.set(c);
      HEX_VALUES[c] = c - 'a' + 10;
    }
  }

  private PercentEncoding() {
  }

  /**
   * Unescapes a URL-encoded string according to the specified unescaping rules.
   *
   * @param input The string to unescape
   * @param unescapeSpec The unescaping specification
   * @param unescapePlus Whether to unescape '+' to space
   * @return The unescaped string
   */
  public static String urlUnescapeString(String input, UrlUnescapeSpec unescapeSpec, boolean unescapePlus) {
    if (input == null || input.isEmpty() || input.indexOf('%') == -1 && (!unescapePlus || input.indexOf('+') == -1)) {
      return input; // Fast path for strings with no escape sequences
    }

    boolean preserveSlash = unescapeSpec == UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_SLASH;
    boolean preserveReserved = unescapeSpec == UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_RESERVED;

    // Preallocate a buffer of the same size (we'll never need more than this)
    int length = input.length();
    char[] buffer = new char[length];
    int pos = 0;

    for (int i = 0; i < length; i++) {
      char c = input.charAt(i);

      if (c == '+' && unescapePlus) {
        buffer[pos++] = ' ';
      } else if (c == '%' && i + 2 < length) {
        // Check if this is a valid percent encoding
        char h1 = input.charAt(i + 1);
        char h2 = input.charAt(i + 2);

        if (isHexDigit(h1) && isHexDigit(h2)) {
          // Decode the hex value
          int decoded = (hexDigitToInt(h1) << 4) | hexDigitToInt(h2);

          // Handle preservation rules
          if ((preserveSlash && decoded == '/') || (preserveReserved && decoded < 128 && RESERVED_CHAR_SET.get(decoded))) {
            // Copy the original percent sequence
            buffer[pos++] = '%';
            buffer[pos++] = h1;
            buffer[pos++] = h2;
          } else {
            // Use the decoded character
            buffer[pos++] = (char) decoded;
          }

          i += 2; // Skip the two hex digits
        } else {
          // Not a valid percent encoding, copy as-is
          buffer[pos++] = c;
        }
      } else {
        // Regular character, copy as-is
        buffer[pos++] = c;
      }
    }

    return new String(buffer, 0, pos);
  }

  /**
   * Check if a character is a valid hex digit.
   */
  private static boolean isHexDigit(char c) {
    return c < 128 && HEX_DIGITS.get(c);
  }

  /**
   * Convert a hex digit to its integer value using precomputed lookup.
   */
  private static int hexDigitToInt(char c) {
    return (c < 128) ? HEX_VALUES[c] : -1;
  }
}
