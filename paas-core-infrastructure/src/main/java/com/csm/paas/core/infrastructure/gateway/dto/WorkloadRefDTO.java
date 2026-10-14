package com.csm.paas.core.infrastructure.gateway.dto;

/**
 * 工作负载的"身份"：Deployment 还是 StatefulSet、`metadata.name` 是什么。
 * <p>网关（防腐层）用它把两个不同 fabric8 类型收敛成同一个返回值，供
 * {@code IServiceRuntimePort.readWorkloadRef} 翻译成领域 VO。
 */
public record WorkloadRefDTO(boolean deployment, String name) {
}
