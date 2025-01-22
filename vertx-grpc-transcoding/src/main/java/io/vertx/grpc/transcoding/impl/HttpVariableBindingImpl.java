package io.vertx.grpc.transcoding.impl;

import io.vertx.grpc.transcoding.HttpVariableBinding;

import java.util.List;

public class HttpVariableBindingImpl implements HttpVariableBinding {

    private List<String> fieldPath;
    private String value;

    public HttpVariableBindingImpl() {
    }

    public HttpVariableBindingImpl(List<String> fieldPath, String value) {
        this.fieldPath = fieldPath;
        this.value = value;
    }

    @Override
    public List<String> getFieldPath() {
        return fieldPath;
    }

    @Override
    public void setFieldPath(List<String> fieldPath) {
        this.fieldPath = fieldPath;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }
}
