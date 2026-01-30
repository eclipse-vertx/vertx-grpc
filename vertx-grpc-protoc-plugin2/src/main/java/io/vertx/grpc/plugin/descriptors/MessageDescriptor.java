package io.vertx.grpc.plugin.descriptors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents metadata and configuration for a Protocol Buffer message type.
 */
public class MessageDescriptor {

  public static final String DEFAULT_NAME = "";
  public static final String DEFAULT_FULL_NAME = "";
  public static final String DEFAULT_JAVA_TYPE = "";

  private String name;
  private String fullName;
  private String javaType;
  private List<FieldDescriptor> fields;
  private Map<String, Object> metadata;

  public MessageDescriptor() {
    this.name = DEFAULT_NAME;
    this.fullName = DEFAULT_FULL_NAME;
    this.javaType = DEFAULT_JAVA_TYPE;
    this.fields = new ArrayList<>();
    this.metadata = new HashMap<>();
  }

  public String getName() {
    return name;
  }

  public MessageDescriptor setName(String name) {
    this.name = name;
    return this;
  }

  public String getFullName() {
    return fullName;
  }

  public MessageDescriptor setFullName(String fullName) {
    this.fullName = fullName;
    return this;
  }

  public String getJavaType() {
    return javaType;
  }

  public MessageDescriptor setJavaType(String javaType) {
    this.javaType = javaType;
    return this;
  }

  public List<FieldDescriptor> getFields() {
    return new ArrayList<>(fields);
  }

  public MessageDescriptor addField(FieldDescriptor field) {
    this.fields.add(field);
    return this;
  }

  public MessageDescriptor setFields(List<FieldDescriptor> fields) {
    this.fields = new ArrayList<>(fields);
    return this;
  }

  public Map<String, Object> getMetadata() {
    return new HashMap<>(metadata);
  }

  public MessageDescriptor addMetadata(String key, Object value) {
    this.metadata.put(key, value);
    return this;
  }

  public MessageDescriptor setMetadata(Map<String, Object> metadata) {
    this.metadata = new HashMap<>(metadata);
    return this;
  }

  /**
   * Represents metadata for a single field within a Protocol Buffer message.
   */
  public static class FieldDescriptor {
    private String name;
    private String jsonName;
    private String type;
    private String javaType;
    private boolean repeated;
    private boolean required;
    private int number;
    private String documentation;

    public FieldDescriptor() {
    }

    public String getName() {
      return name;
    }

    public FieldDescriptor setName(String name) {
      this.name = name;
      return this;
    }

    public String getJsonName() {
      return jsonName;
    }

    public FieldDescriptor setJsonName(String jsonName) {
      this.jsonName = jsonName;
      return this;
    }

    public String getType() {
      return type;
    }

    public FieldDescriptor setType(String type) {
      this.type = type;
      return this;
    }

    public String getJavaType() {
      return javaType;
    }

    public FieldDescriptor setJavaType(String javaType) {
      this.javaType = javaType;
      return this;
    }

    public boolean isRepeated() {
      return repeated;
    }

    public FieldDescriptor setRepeated(boolean repeated) {
      this.repeated = repeated;
      return this;
    }

    public boolean isRequired() {
      return required;
    }

    public FieldDescriptor setRequired(boolean required) {
      this.required = required;
      return this;
    }

    public int getNumber() {
      return number;
    }

    public FieldDescriptor setNumber(int number) {
      this.number = number;
      return this;
    }

    public String getDocumentation() {
      return documentation;
    }

    public FieldDescriptor setDocumentation(String documentation) {
      this.documentation = documentation;
      return this;
    }
  }
}
