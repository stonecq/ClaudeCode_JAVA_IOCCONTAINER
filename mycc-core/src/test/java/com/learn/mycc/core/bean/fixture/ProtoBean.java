package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Scope;
import com.learn.mycc.core.annotation.ScopeType;
import com.learn.mycc.core.bean.InitializingBean;
import com.learn.mycc.core.context.fixture.LifecycleRecorder;

/** 原型作用域测试夹具：每次创建记录一次 init 事件，供验证 preInstantiate 跳过原型。 */
@Scope(ScopeType.PROTOTYPE)
public class ProtoBean implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        LifecycleRecorder.record("init:protoBean");
    }
}