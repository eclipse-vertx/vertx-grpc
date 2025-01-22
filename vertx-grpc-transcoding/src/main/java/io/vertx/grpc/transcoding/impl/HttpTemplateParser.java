package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.transcoding.HttpTemplateVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * A parser for HTTP template strings used in gRPC transcoding.
 * <p>
 * This class parses HTTP template strings of the format defined in the gRPC HTTP transcoding specification. It extracts variables, segments, and the verb from the template
 * string.
 * <p>
 * For example, the template string {@code "/users/{user_id=*}:get"} would be parsed into:
 * <ul>
 *   <li>verb: "get"</li>
 *   <li>segments: ["users", "{user_id=*}"]</li>
 *   <li>variables: [HttpTemplateVariableImpl{fieldPath=["user_id"], startSegment=1, endSegment=2, wildcardPath=true}]</li>
 * </ul>
 */
public class HttpTemplateParser {
  public static final String SINGLE_PARAMETER_KEY = "/.";
  public static final String WILD_CARD_PATH_PART_KEY = "*";
  public static final String WILD_CARD_PATH_KEY = "**";

  private final String templateString;

  private final List<String> segments = new ArrayList<>();
  private final List<HttpTemplateVariable> variables = new ArrayList<>();

  private String verb = "";

  private int tokenBegin;
  private int tokenEnd;
  private boolean parsingVariable;

  public HttpTemplateParser(String templateString) {
    this.templateString = templateString;
    this.tokenBegin = 0;
    this.tokenEnd = 0;
    this.parsingVariable = false;
  }

  public boolean parse() {
    if (!parseTemplate() || !allInputConsumed()) {
      return false;
    }
    finalizeVariables();
    return true;
  }

  public List<String> segments() {
    return segments;
  }

  public String verb() {
    return verb;
  }

  public List<HttpTemplateVariable> variables() {
    return variables;
  }

  public boolean validateParts() {
    boolean foundWildcardPath = false;
    for (String segment : segments) {
      if (!foundWildcardPath) {
        if (segment.equals(WILD_CARD_PATH_KEY)) {
          foundWildcardPath = true;
        }
      } else if (segment.equals(SINGLE_PARAMETER_KEY) ||
        segment.equals(WILD_CARD_PATH_PART_KEY) ||
        segment.equals(WILD_CARD_PATH_KEY)) {
        return false;
      }
    }
    return true;
  }

  private boolean parseTemplate() {
    if (!consumeCharacter('/')) {
      return false;
    }
    if (!parseSegments()) {
      return false;
    }

    if (hasMoreCharacters() && currentChar() == ':') {
      return parseVerb();
    }
    return true;
  }

  private boolean parseSegments() {
    do {
      if (!parseSegment()) {
        return false;
      }
    } while (consumeCharacter('/'));

    return true;
  }

  private boolean parseSegment() {
    if (!hasMoreCharacters()) {
      return false;
    }
    switch (currentChar()) {
      case '*': {
        consumeCharacter('*');
        if (consumeCharacter('*')) {
          segments.add("**");
          if (parsingVariable) {
            return markVariableAsWildcard();
          }
        } else {
          segments.add("*");
        }
        return true;
      }

      case '{':
        return parseVariable();
      default:
        return parseLiteralSegment();
    }
  }

  private boolean parseVariable() {
    if (!consumeCharacter('{')) {
      return false;
    }
    if (!beginVariableParsing()) {
      return false;
    }
    if (!parseFieldPath()) {
      return false;
    }
    if (consumeCharacter('=')) {
      if (!parseSegments()) {
        return false;
      }
    } else {
      // {fieldPath} is equivalent to {fieldPath=*}
      segments.add("*");
    }
    if (!endVariableParsing()) {
      return false;
    }

    return consumeCharacter('}');
  }

  private boolean parseLiteralSegment() {
    StringBuilder literalBuilder = new StringBuilder();
    if (!parseLiteral(literalBuilder)) {
      return false;
    }
    segments.add(literalBuilder.toString());
    return true;
  }

  private boolean parseFieldPath() {
    do {
      if (!parseIdentifier()) {
        return false;
      }
    } while (consumeCharacter('.'));
    return true;
  }

  private boolean parseVerb() {
    if (!consumeCharacter(':')) {
      return false;
    }
    StringBuilder verbBuilder = new StringBuilder();
    if (!parseLiteral(verbBuilder)) {
      return false;
    }
    verb = verbBuilder.toString();
    return true;
  }

  private boolean parseIdentifier() {
    StringBuilder identifierBuilder = new StringBuilder();
    boolean hasContent = false;

    while (advanceToken()) {
      char currentCharacter = currentChar();
      switch (currentCharacter) {
        case '.':
        case '}':
        case '=':
          return hasContent && addFieldPathIdentifier(identifierBuilder.toString());
        default:
          consumeCharacter(currentCharacter);
          identifierBuilder.append(currentCharacter);
          break;
      }
      hasContent = true;
    }
    return hasContent && addFieldPathIdentifier(identifierBuilder.toString());
  }

  private boolean parseLiteral(StringBuilder literalBuilder) {
    if (!hasMoreCharacters()) {
      return false;
    }

    boolean hasContent = false;

    while (true) {
      char currentCharacter = currentChar();
      switch (currentCharacter) {
        case '/':
        case ':':
        case '}':
          return hasContent;
        default:
          consumeCharacter(currentCharacter);
          literalBuilder.append(currentCharacter);
          break;
      }

      hasContent = true;

      if (!advanceToken()) {
        break;
      }
    }
    return hasContent;
  }

  private boolean consumeCharacter(char expected) {
    if (tokenBegin >= tokenEnd && !advanceToken()) {
      return false;
    }
    if (currentChar() != expected) {
      return false;
    }
    tokenBegin++;
    return true;
  }

  private boolean allInputConsumed() {
    return tokenBegin >= templateString.length();
  }

  private boolean hasMoreCharacters() {
    return tokenBegin < tokenEnd || advanceToken();
  }

  private boolean advanceToken() {
    if (tokenEnd < templateString.length()) {
      tokenEnd++;
      return true;
    }
    return false;
  }

  private char currentChar() {
    return tokenBegin < tokenEnd && tokenEnd <= templateString.length() ? templateString.charAt(tokenEnd - 1) : (char) -1;
  }

  private HttpTemplateVariable getCurrentVariable() {
    return variables.get(variables.size() - 1);
  }

  private boolean beginVariableParsing() {
    if (!parsingVariable) {
      variables.add(new HttpTemplateVariableImpl());
      getCurrentVariable().setStartSegment(segments.size());
      getCurrentVariable().setWildcardPath(false);
      parsingVariable = true;
      return true;
    }
    return false; // Nested variables are not allowed
  }

  private boolean endVariableParsing() {
    if (parsingVariable && !variables.isEmpty()) {
      HttpTemplateVariable variable = getCurrentVariable();
      variable.setEndSegment(segments.size());
      parsingVariable = false;
      return validateVariable(variable);
    }
    return false; // Not currently parsing a variable
  }

  private boolean addFieldPathIdentifier(String identifier) {
    if (parsingVariable && !variables.isEmpty()) {
      getCurrentVariable().getFieldPath().add(identifier);
      return true;
    }
    return false; // Not currently parsing a variable
  }

  private boolean markVariableAsWildcard() {
    if (parsingVariable && !variables.isEmpty()) {
      getCurrentVariable().setWildcardPath(true);
      return true;
    }
    return false; // Not currently parsing a variable
  }

  private boolean validateVariable(HttpTemplateVariable variable) {
    return !variable.getFieldPath().isEmpty()
      && (variable.getStartSegment() < variable.getEndSegment())
      && (variable.getEndSegment() <= segments.size());
  }

  private void finalizeVariables() {
    for (HttpTemplateVariable variable : variables) {
      if (variable.hasWildcardPath()) {
        // For wildcard paths ('**'), store end position relative to path end
        // -1 corresponds to end of path, allowing matcher to reconstruct
        // variable value from URL segments for fixed paths after '**'
        variable.setEndSegment(variable.getEndSegment() - segments.size() - 1);
      }
    }
  }
}
