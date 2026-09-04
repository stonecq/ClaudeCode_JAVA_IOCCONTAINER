package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.bean.BeanPostProcessor;

import java.util.ArrayList;
import java.util.List;

/** 记录被后置处理 bean 名称的 BeanPostProcessor，验证 @Bean 产物走 BPP。 */
public final class RecordingBpp implements BeanPostProcessor {

    public static final List<String> PROCESSED = new ArrayList<>();

    public static void clear() {
        PROCESSED.clear();
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        PROCESSED.add(beanName);
        return bean;
    }
}