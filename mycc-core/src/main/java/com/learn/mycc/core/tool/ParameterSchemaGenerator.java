package com.learn.mycc.core.tool;

import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.exception.MyccException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 @Tool 方法参数反射生成 JSON Schema（type / description / required / enum）。 */
public final class ParameterSchemaGenerator {

    public Map<String, Object> generate(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            String name = parameter.getName();
            Map<String, Object> property = typeSchema(parameter.getType());
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (toolParam != null && !toolParam.description().isEmpty()) {
                property.put("description", toolParam.description());
            }
            properties.put(name, property);
            if (toolParam == null || toolParam.required()) {
                required.add(name);
            }
        }
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> typeSchema(Class<?> type) {
        Map<String, Object> property = new LinkedHashMap<>();
        if (type == String.class) {
            property.put("type", "string");
        } else if (type.isEnum()) {
            property.put("type", "string");
            List<String> constants = Arrays.stream(type.getEnumConstants())
                    .map(value -> ((Enum<?>) value).name())
                    .toList();
            property.put("enum", constants);
        } else if (type == boolean.class || type == Boolean.class) {
            property.put("type", "boolean");
        } else if (isInteger(type)) {
            property.put("type", "integer");
        } else if (isNumber(type)) {
            property.put("type", "number");
        } else {
            throw new MyccException("不支持的参数类型: " + type.getName());
        }
        return property;
    }

    private boolean isInteger(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class;
    }

    private boolean isNumber(Class<?> type) {
        return type == double.class || type == Double.class
                || type == float.class || type == Float.class;
    }
}
