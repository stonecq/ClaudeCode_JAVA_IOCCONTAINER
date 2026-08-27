package com.learn.mycc.core.bean;

/** 生命周期回调：容器 close 时按创建逆序调用，用于释放资源。 */
public interface DisposableBean {

    void destroy();
}
