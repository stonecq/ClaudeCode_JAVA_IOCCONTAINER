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

/**
 * 从 @Tool 方法参数反射生成 JSON Schema（供 LLM 工具调用使用）。
 * 输出形如 {"type":"object","properties":{...},"required":[...]}，
 * 每个参数映射为 properties 的一项：type / 可选 description / enum（枚举参数）。
 * 仅支持基础类型（字符串、布尔、整数、浮点、枚举），
 * 复杂类型会在 {@link #typeSchema} 中抛异常。
 */
public final class ParameterSchemaGenerator {

    /**
     * 为方法生成参数 JSON Schema。
     * 参数名取自反射（需编译期 -parameters 选项，否则为 arg0 形式）；
     * 是否必填由 {@link ToolParam#required()} 决定，未标注 @ToolParam 的参数默认必填。
     *
     * @param method @Tool 方法
     * @return 参数 JSON Schema（Map 表示，可直接序列化）
     * @throws com.learn.mycc.core.exception.MyccException 存在不支持的参数类型时抛出
     */
    public Map<String, Object> generate(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        // LinkedHashMap 保证参数/属性按声明顺序呈现，Schema 输出稳定、可读
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            String name = parameter.getName();
            Map<String, Object> property = typeSchema(parameter.getType());
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            // description 非空才写入，避免 schema 出现无意义的空串字段
            if (toolParam != null && !toolParam.description().isEmpty()) {
                property.put("description", toolParam.description());
            }
            properties.put(name, property);
            // 未标注 @ToolParam 或显式 required()==true → 该参数必填
            if (toolParam == null || toolParam.required()) {
                required.add(name);
            }
        }
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    /**
     * 将单个参数的 Java 类型映射为 JSON Schema 的 type。
     * 映射规则：String→string；枚举→string + enum 常量列表；boolean→boolean；
     * 整型→integer；浮点→number；其余类型不支持。
     *
     * @param type 参数类型
     * @return 该参数的 schema（含 type，及枚举参数的 enum）
     * @throws com.learn.mycc.core.exception.MyccException 不支持的参数类型时抛出
     */
    private Map<String, Object> typeSchema(Class<?> type) {
        Map<String, Object> property = new LinkedHashMap<>();
        if (type == String.class) {
            property.put("type", "string");
        } else if (type.isEnum()) {
            property.put("type", "string");
            // 枚举以字符串常量名呈现，供 LLM 从有限候选中取值，规避自由文本
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

    /**
     * @param type 待判定类型
     * @return 是否为整数类型（含原始类型与包装类型 byte/short/int/long）
     */
    private boolean isInteger(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class;
    }

    /**
     * @param type 待判定类型
     * @return 是否为浮点类型（含原始类型与包装类型 float/double）
     */
    private boolean isNumber(Class<?> type) {
        return type == double.class || type == Double.class
                || type == float.class || type == Float.class;
    }
}
