package com.learn.mycc.core.bean;

/**
 * 生命周期回调（初始化阶段）：bean 装配（依赖注入）完成后、
 * 放入单例缓存前由容器调用，
 * 供 bean 在依赖就绪后执行校验或开启内部状态（对标 Spring 的 InitializingBean）。
 */
public interface InitializingBean {

    /** 依赖注入已完成，可在此执行依赖校验等初始化逻辑；
     *  失败抛异常会中止该 bean 的创建。 */
    void afterPropertiesSet();
}
