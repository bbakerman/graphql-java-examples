package com.graphql.example.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * This example code chose to use ackson as its JSON parser. Any JSON parser should be fine
 */
public class JsonKit {
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    public static void toJson(HttpServletResponse response, Object result) throws IOException {
        OBJECT_MAPPER.writeValue(response.getWriter(), result);
    }

    public static Map<String, Object> toMap(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().length() == 0) {
            return Collections.emptyMap();
        }
        TypeReference<Map<String, Object>> typeToken = new TypeReference<Map<String, Object>>() {
        };
        Map<String, Object> map;
        try {
            map = OBJECT_MAPPER.readValue(jsonStr, typeToken);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return map == null ? Collections.emptyMap() : map;
    }

    public static Object fromJson(String jsonStr) throws IOException {
        return OBJECT_MAPPER.readValue(jsonStr, Object.class);
    }
}
