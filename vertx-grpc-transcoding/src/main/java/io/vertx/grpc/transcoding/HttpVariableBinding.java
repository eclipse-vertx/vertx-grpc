package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.DataObject;

import java.util.List;

/**
 * Represents a binding between an HTTP request variable (from path or query parameters) and its corresponding gRPC message field path. This interface is used during HTTP-to-gRPC
 * transcoding to map HTTP request variables to their appropriate locations in the gRPC message.
 *
 * The binding consists of: - A field path representing the location in the gRPC message where the value should be placed - The actual value extracted from the HTTP request
 */
@DataObject
public class HttpVariableBinding {

    private List<String> fieldPath;
    private String value;

    public HttpVariableBinding() {
    }

    public HttpVariableBinding(List<String> fieldPath, String value) {
        this.fieldPath = fieldPath;
        this.value = value;
    }

    /**
     * Gets the field path that describes where in the gRPC message the value should be placed.
     *
     * @return A list of field names representing the path in the gRPC message
     */
    public List<String> getFieldPath() {
        return fieldPath;
    }

    /**
     * Sets the field path that describes where in the gRPC message the value should be placed.
     *
     * @param fieldPath A list of field names representing the path in the gRPC message
     */
    public void setFieldPath(List<String> fieldPath) {
        this.fieldPath = fieldPath;
    }

    /**
     * Gets the value that was extracted from the HTTP request.
     *
     * @return The string value to be bound to the specified field path
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value that should be bound to the specified field path.
     *
     * @param value The string value to be bound to the specified field path
     */
    public void setValue(String value) {
        this.value = value;
    }
}
