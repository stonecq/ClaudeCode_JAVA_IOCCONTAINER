package com.learn.mycc.core.bean.fixture;

/** 工厂方法形参按类型注入的测试夹具：构造器接收一个被注入的 FieldDep。 */
public class DepConsumer {

    private final FieldDep dep;

    public DepConsumer(FieldDep dep) {
        this.dep = dep;
    }

    public FieldDep getDep() {
        return dep;
    }
}