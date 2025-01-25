package io.vertx.grpc.transcoding;

import io.vertx.codegen.annotations.DataObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a variable within an HTTP template used in gRPC transcoding.
 * <p>
 * This interface defines methods for accessing information about a variable, such as its field path, start and end segments, and whether it represents a wildcard path.
 *
 * @author Based on <a href="https://github.com/grpc-ecosystem/grpc-httpjson-transcoding/blob/master/src/http_template.cc">grpc-httpjson-transcoding</a>
 */
@DataObject
public class HttpTemplateVariable {
    private List<String> fieldPath;
    private int startSegment;
    private int endSegment;
    private boolean wildcardPath;

    public HttpTemplateVariable() {
        this.fieldPath = new ArrayList<>();
        this.startSegment = 0;
        this.endSegment = 0;
        this.wildcardPath = false;
    }

    public HttpTemplateVariable(List<String> fieldPath, int startSegment, int endSegment, boolean wildcardPath) {
        this.fieldPath = fieldPath;
        this.startSegment = startSegment;
        this.endSegment = endSegment;
        this.wildcardPath = wildcardPath;
    }

    /**
     * Returns the field path of the variable.
     *
     * @return The field path.
     */
    public List<String> getFieldPath() {
        return fieldPath;
    }

    /**
     * Sets the field path of the variable.
     *
     * @param fieldPath The field path to set.
     */
    public void setFieldPath(List<String> fieldPath) {
        this.fieldPath = fieldPath;
    }

    /**
     * Returns the starting segment of the variable.
     *
     * @return The starting segment.
     */
    public int getStartSegment() {
        return startSegment;
    }

    /**
     * Sets the starting segment of the variable.
     *
     * @param startSegment The starting segment to set.
     */
    public void setStartSegment(int startSegment) {
        this.startSegment = startSegment;
    }

    /**
     * Returns the ending segment of the variable.
     *
     * @return The ending segment.
     */
    public int getEndSegment() {
        return endSegment;
    }

    /**
     * Sets the ending segment of the variable.
     *
     * @param endSegment The ending segment to set.
     */
    public void setEndSegment(int endSegment) {
        this.endSegment = endSegment;
    }

    /**
     * Checks if the variable represents a wildcard path.
     *
     * @return {@code true} if the variable represents a wildcard path, {@code false} otherwise.
     */
    public boolean hasWildcardPath() {
        return wildcardPath;
    }

    /**
     * Sets whether the variable represents a wildcard path.
     *
     * @param wildcardPath {@code true} if the variable represents a wildcard path, {@code false} otherwise.
     */
    public void setWildcardPath(boolean wildcardPath) {
        this.wildcardPath = wildcardPath;
    }
}
