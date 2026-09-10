package com.learn.mycc.agent.planning;

/**
 * 计划步骤：一条待执行/已执行的步骤。
 * @param description 步骤内容描述
 * @param done        是否已完成
 */
public record PlanStep(String description, boolean done) {
}