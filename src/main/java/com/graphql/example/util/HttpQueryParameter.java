package com.graphql.example.util;

public class HttpQueryParameter {
    final String name;
    final String value;

    public HttpQueryParameter(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public static HttpQueryParameter qp(String name, Object value) {
        return new HttpQueryParameter(name, value.toString());
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
