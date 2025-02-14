package io.vertx.grpc.transcoding.impl;

import java.util.regex.Pattern;

/**
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/include/grpc_transcoding/percent_encoding.h">grpc-httpjson-transcoding</a>
 */
public class PercentEncoding {

  private static final Pattern ESCAPE_STRING_PATTERN = Pattern.compile("[^a-zA-Z0-9-_.~]");

  public enum UrlUnescapeSpec {
    ALL_CHARACTERS_EXCEPT_RESERVED,
    ALL_CHARACTERS_EXCEPT_SLASH,
    ALL_CHARACTERS
  }

  private static boolean isReservedChar(char c) {
    // Reserved characters according to RFC 6570
    switch (c) {
      case '!':
      case '#':
      case '$':
      case '&':
      case '\'':
      case '(':
      case ')':
      case '*':
      case '+':
      case ',':
      case '/':
      case ':':
      case ';':
      case '=':
      case '?':
      case '@':
      case '[':
      case ']':
        return true;
      default:
        return false;
    }
  }

  private static boolean asciiIsxdigit(char c) {
    return ('a' <= c && c <= 'f')
      || ('A' <= c && c <= 'F')
      || ('0' <= c && c <= '9');
  }

  private static int hexDigitToInt(char c) {
    /* Assume ASCII. */
    int x = (int) c;
    if (x > '9') {
      x += 9;
    }
    return x & 0xf;
  }

  private static int getEscapedChar(String src, int i, UrlUnescapeSpec unescapeSpec, boolean unescapePlus, char[] out) {
    if (unescapePlus && src.charAt(i) == '+') {
      out[0] = ' ';
      return 1;
    }
    if (i + 2 < src.length() && src.charAt(i) == '%') {
      if (asciiIsxdigit(src.charAt(i + 1)) && asciiIsxdigit(src.charAt(i + 2))) {
        char c =
          (char) ((hexDigitToInt(src.charAt(i + 1)) << 4) | hexDigitToInt(src.charAt(i + 2)));
        switch (unescapeSpec) {
          case ALL_CHARACTERS_EXCEPT_RESERVED:
            if (isReservedChar(c)) {
              return 0;
            }
            break;
          case ALL_CHARACTERS_EXCEPT_SLASH:
            if (c == '/') {
              return 0;
            }
            break;
          case ALL_CHARACTERS:
            break;
        }
        out[0] = c;
        return 3;
      }
    }
    return 0;
  }

  public static boolean isUrlEscapedString(String part, UrlUnescapeSpec unescapeSpec, boolean unescapePlus) {
    char[] ch = new char[1];
    for (int i = 0; i < part.length(); ++i) {
      if (getEscapedChar(part, i, unescapeSpec, unescapePlus, ch) > 0) {
        return true;
      }
    }
    return false;
  }

  public static boolean isUrlEscapedString(String part) {
    return isUrlEscapedString(part, UrlUnescapeSpec.ALL_CHARACTERS, false);
  }

  public static String urlUnescapeString(String part, UrlUnescapeSpec unescapeSpec, boolean unescapePlus) {
    // Check whether we need to escape at all.
    if (!isUrlEscapedString(part, unescapeSpec, unescapePlus)) {
      return part;
    }

    StringBuilder unescaped = new StringBuilder(part.length());
    char[] ch = new char[1];

    for (int i = 0; i < part.length(); ) {
      int skip = getEscapedChar(part, i, unescapeSpec, unescapePlus, ch);
      if (skip > 0) {
        unescaped.append(ch[0]);
        i += skip;
      } else {
        unescaped.append(part.charAt(i));
        i += 1;
      }
    }

    return unescaped.toString();
  }

  /** Remove this in the future, if not needed */
  private static String urlUnescapeString(String part) {
    return urlUnescapeString(part, UrlUnescapeSpec.ALL_CHARACTERS, false);
  }

  /** Remove this in the future, if not needed */
  private static String urlEscapeString(String str) {
    return ESCAPE_STRING_PATTERN.matcher(str).replaceAll(m -> "%" + Integer.toHexString(m.group().charAt(0)).toUpperCase());
  }
}
