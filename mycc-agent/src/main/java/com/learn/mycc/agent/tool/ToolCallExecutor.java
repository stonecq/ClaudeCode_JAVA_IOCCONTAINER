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

/**
 * 执行 LLM 发起的工具调用：查注册表 → JSON 参数绑定 → 反射调用 → ToolResult。
 * 失败不抛异常，而是封装为失败结果回填给 LLM。
 * 设计意图：把"工具名称→方法调用"的反射/解析细节收敛在此，
 * agent 主循环只关心拿到结果，
 * 且任何失败都不让会话流程被工具错误打断。
 */
public final class ToolCallExecutor {

    private final ToolRegistry toolRegistry;
    /** JSON 参数解析器（实例级，独立使用不共享）。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /** @param toolRegistry 工具注册表：按名称查找已注册的 @Tool 方法定义。 */
    public ToolCallExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行一次工具调用。
     * 统一捕获各类异常并转为失败 {@link ToolResult}：
     * 工具不存在/参数不足/反射调用异常/运行期异常，
     * 均以失败结果连同错误信息返回，由调用方回填给 LLM。
     * @param call LLM 发起的工具调用（含工具名、参数 JSON 字符串）
     * @return 执行结果：成功或失败，均不抛异常；失败时 output 为错误描述
     */
    public ToolResult execute(ToolCall call) {
        try {
            ToolDefinition definition = toolRegistry.get(call.name());
            Object[] args = bindArguments(definition.getMethod(), call.arguments());
            Object result = definition.getMethod().invoke(definition.getBean(), args);
            return ToolResult.ok(call.id(), String.valueOf(result));
        } catch (MyccException e) {
            // 参数绑定/数量不匹配等明确错误。
            return ToolResult.failure(call.id(), e.getMessage());
        } catch (ReflectiveOperationException e) {
            // 反射失败：优先取被调方法抛出的原始异常（cause），否则用反射包装层异常。
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return ToolResult.failure(call.id(), String.valueOf(cause.getMessage()));
        } catch (Exception e) {
            // 其余运行期异常兜底。
            return ToolResult.failure(call.id(), String.valueOf(e.getMessage()));
        }
    }

    /** 把工具参数 JSON 绑定为方法入参数组；空参方法直接返回空数组，避免多余解析。
     *  @throws JsonProcessingException JSON 解析失败时抛出 */
    private Object[] bindArguments(Method method, String arguments) throws JsonProcessingException {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        if (parameters.length == 0) {
            return args;
        }
        // 无参数 JSON 时按空对象 {} 处理，按参数名逐一取节点转换；
        // 多余字段忽略，缺少必填字段在 convert 中报错。
        JsonNode root = mapper.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            args[i] = convert(root.get(parameter.getName()), parameter.getType());
        }
        return args;
    }

    /** 把 JSON 节点转换成 Java 参数类型；仅当值为 null 且目标为基本类型时才报缺参错误。
     *  @throws JsonProcessingException 复杂对象（POJO）反序列化失败时抛出 */
    private Object convert(JsonNode value, Class<?> type) throws JsonProcessingException {
        if (value == null || value.isNull()) {
            // 基本类型（无 null 概念）缺参即错误；对象类型允许 null。
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
        // 其余类型（POJO/List 等）委托 Jackson 转换。
        return mapper.treeToValue(value, type);
    }
}
