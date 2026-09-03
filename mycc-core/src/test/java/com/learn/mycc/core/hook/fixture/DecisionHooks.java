package com.learn.mycc.core.hook.fixture;

import com.learn.mycc.core.hook.HookDecision;
import com.learn.mycc.core.hook.HookEvent;

import java.util.ArrayList;
import java.util.List;

/** 测试用：返回 HookDecision 的否决型钩子，供 Dispatcher 否决用例直接构造。 */
public class DecisionHooks {

    public static final List<String> CALLED = new ArrayList<>();

    public static void clear() {
        CALLED.clear();
    }

    public HookDecision allow(HookEvent event) {
        CALLED.add("allow");
        return HookDecision.ALLOW;
    }

    public HookDecision deny(HookEvent event) {
        CALLED.add("deny");
        return HookDecision.deny("blocked");
    }

    public HookDecision afterDeny(HookEvent event) {
        CALLED.add("afterDeny");
        return HookDecision.ALLOW;
    }
}