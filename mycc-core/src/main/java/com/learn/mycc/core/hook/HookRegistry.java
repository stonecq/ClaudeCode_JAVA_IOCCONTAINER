package com.learn.mycc.core.hook;

import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.bean.BeanPostProcessor;
import com.learn.mycc.core.exception.MyccException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 钩子注册表：实现 {@link BeanPostProcessor}，在 bean 创建完成后反射扫描其
 * {@link Hook} 方法并按事件类型注册。与 {@link com.learn.mycc.core.tool.ToolRegistry}
 * 同构——「被动捕获」：只要把本实例 add 为后置处理器，任何组件后续创建的 @Hook
 * 都会被自动收集，与 IoC 容器天然解耦。
 * 事件类型 → 订阅者列表用 {@link EnumMap} 存储，保证按枚举取值、遍历顺序稳定。
 */
public final class HookRegistry implements BeanPostProcessor {

    /** 事件类型 → 该事件的钩子定义列表（按注册顺序）；EnumMap 保证键覆盖全部枚举。 */
    private final Map<HookEventType, List<HookDefinition>> hooksByEvent = new EnumMap<>(HookEventType.class);

    /**
     * 容器回调：扫描 bean 声明方法中带 @Hook 的方法并注册；未标注的方法被忽略。
     * 事件名非法或方法签名错误在此处即抛错，实现「启动期快速失败」。
     *
     * @param bean     创建完成的 bean 实例
     * @param beanName bean 名称（本实现不使用）
     * @return 原 bean 实例（本后置处理器不改造实例）
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            Hook hook = method.getAnnotation(Hook.class);
            if (hook != null) {
                register(new HookDefinition(HookEventType.fromName(hook.event()), bean, method));
            }
        }
        return bean;
    }

    /**
     * 注册单个钩子定义。同一事件可注册多个订阅者，按注册顺序追加。
     *
     * @param definition 钩子定义，不允许为 null
     * @throws MyccException 方法签名不是「单个 HookEvent 参数」时抛出
     */
    public void register(HookDefinition definition) {
        validate(definition.getMethod());
        hooksByEvent.computeIfAbsent(definition.getEventType(), key -> new ArrayList<>()).add(definition);
    }

    /**
     * 按事件类型取订阅者快照。
     *
     * @param type 事件类型
     * @return 该事件的钩子定义列表（不可变，按注册顺序）；无订阅者时返回空列表
     */
    public List<HookDefinition> get(HookEventType type) {
        return List.copyOf(hooksByEvent.getOrDefault(type, List.of()));
    }

    /**
     * @return 全部已注册钩子定义快照（不可变，按事件类型分组、组内按注册顺序）
     */
    public List<HookDefinition> getAll() {
        List<HookDefinition> all = new ArrayList<>();
        for (List<HookDefinition> definitions : hooksByEvent.values()) {
            all.addAll(definitions);
        }
        return List.copyOf(all);
    }

    /**
     * 校验钩子方法签名：必须是单个 {@link HookEvent} 参数，否则启动期报错。
     *
     * @param method 待校验的钩子方法
     * @throws MyccException 参数个数或类型不符合约定时抛出
     */
    private static void validate(Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length != 1 || parameters[0].getType() != HookEvent.class) {
            throw new MyccException("钩子方法签名必须为单个 HookEvent 参数: " + method);
        }
    }
}
