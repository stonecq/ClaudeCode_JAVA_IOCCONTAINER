package com.learn.mycc.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.tool.ToolDefinition;
import com.learn.mycc.core.tool.ToolRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/** 执行 LLM 发起的工具调用：查注册表 → JSON 参数绑定 → 反射调用 → ToolResult（失败不抛，回填给 LLM）。 */
public final class ToolCallExecutor {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolCallExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ToolResult execute(ToolCall call) {
        try {
            ToolDefinition definition = toolRegistry.get(call.name());
            Object[] args = bindArguments(definition.getMethod(), call.arguments());
            Object result = definition.getMethod().invoke(definition.getBean(), args);
            return ToolResult.ok(call.id(), String.valueOf(result));
        } catch (MyccException e) {
            return ToolResult.failure(call.id(), e.getMessage());
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return ToolResult.failure(call.id(), String.valueOf(cause.getMessage()));
        } catch (Exception e) {
            return ToolResult.failure(call.id(), String.valueOf(e.getMessage()));
        }
    }

    private Object[] bindArguments(Method method, String arguments) throws JsonProcessingException {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        if (parameters.length == 0) {
            return args;
        }
        JsonNode root = mapper.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            args[i] = convert(root.get(parameter.getName()), parameter.getType());
        }
        return args;
    }

    private Object convert(JsonNode value, Class<?> type) throws JsonProcessingException {
        if (value == null || value.isNull()) {
            if (type.isPrimitive()) {
                throw new MyccException("缺少参数: " + type.getSimpleName());
            }
            return null;
        }
        if (type == String.class) {
            return value.asText();
        }
        if (type == boolean.class || type == Boolean.class) {
            return value.asBoolean();
        }
        if (type == int.class || type == Integer.class) {
            return value.asInt();
        }
        if (type == long.class || type == Long.class) {
            return value.asLong();
        }
        if (type == double.class || type == Double.class) {
            return value.asDouble();
        }
        if (type.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> enumType = (Class<Enum>) type;
            return Enum.valueOf(enumType, value.asText());
        }
        return mapper.treeToValue(value, type);
    }
}
