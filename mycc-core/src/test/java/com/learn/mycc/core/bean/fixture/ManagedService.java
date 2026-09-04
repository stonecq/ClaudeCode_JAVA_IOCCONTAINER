package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.context.fixture.LifecycleRecorder;

/** @Bean destroyMethod 测试夹具：shutdown 在容器关闭时被反射调用并记录事件。 */
public class ManagedService {

    public void shutdown() {
        LifecycleRecorder.record("shutdown:managedService");
    }
}