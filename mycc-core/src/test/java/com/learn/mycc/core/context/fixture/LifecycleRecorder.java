package com.learn.mycc.core.context.fixture;

import java.util.ArrayList;
import java.util.List;

/** 生命周期回调事件记录器，供测试断言调用顺序。 */
public final class LifecycleRecorder {

    public static final List<String> EVENTS = new ArrayList<>();

    private LifecycleRecorder() {
    }

    public static void clear() {
        EVENTS.clear();
    }

    public static void record(String event) {
        EVENTS.add(event);
    }
}
