package com.learn.mycc.core.bean;

/** 生命周期回调：bean 装配（依赖注入）完成后调用。 */
public interface InitializingBean {

    void afterPropertiesSet();
}
