package com.learn.mycc.core.hook.fixture;

import com.learn.mycc.core.hook.HookEvent;

import java.util.ArrayList;
import java.util.List;

/** 测试用：带多个同名事件方法的组件，供 Dispatcher 按显式注册顺序与异常容忍用例直接构造。 */
public class OrderedHooks {

    public static final List<String> CALLED = new ArrayList<>();

    public static void clear() {
        CALLED.clear();
    }

    public void first(HookEvent event) {
        CALLED.add("first");
    }

    public void second(HookEvent event) {
        CALLED.add("second");
    }

    public void boom(HookEvent event) {
        throw new IllegalStateException("boom");
    }

    public void after(HookEvent event) {
        CALLED.add("after");
    }
}
