package com.learn.mycc.core.annotation;

/**
 * 工具风险等级（M8 权限管理的风险声明）。
 * <p>HIGH 风险的工具（bash、写文件等）调用默认触发审批，规则文件可覆盖；
 * LOW 风险工具默认直通。当前仅两级，需要更细粒度时再扩展。</p>
 */
public enum ToolRisk {
    LOW,
    HIGH
}