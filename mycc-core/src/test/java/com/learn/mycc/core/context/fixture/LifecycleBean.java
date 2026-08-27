package com.learn.mycc.core.context.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.bean.DisposableBean;
import com.learn.mycc.core.bean.InitializingBean;

@Component
public class LifecycleBean implements InitializingBean, DisposableBean {

    @Override
    public void afterPropertiesSet() {
        LifecycleRecorder.record("init:lifecycleBean");
    }

    @Override
    public void destroy() {
        LifecycleRecorder.record("destroy:lifecycleBean");
    }
}
