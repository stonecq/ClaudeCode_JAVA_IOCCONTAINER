package com.learn.mycc.core.bean;

/**
 * 生命周期回调（销毁阶段）：容器 close 时按创建逆序调用，
 * 用于释放外部资源（关闭连接、清理缓存等）。
 * 实现由 {@link com.learn.mycc.core.bean.BeanFactory#close()} 触发。
 */
public interface DisposableBean {

    /** 释放该 bean 持有的资源；失败不应中断其它 bean 的销毁。 */
    void destroy();
}
