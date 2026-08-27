package com.learn.mycc.core.context.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.bean.DisposableBean;
import com.learn.mycc.core.bean.InitializingBean;

@Component
public class SecondBean implements InitializingBean, DisposableBean {

    @Override
    public void afterPropertiesSet() {
        LifecycleRecorder.record("init:secondBean");
    }

    @Override
    public void destroy() {
        LifecycleRecorder.record("destroy:secondBean");
    }
}
