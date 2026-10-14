package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `GET /v1/service/event` 需要的那两个 pod 字段。
 * <p>为什么单独一个 VO：厂商用 `metadata.generateName` **去掉最后一个字符**当作 ReplicaSet 名
 * （`generateName` 形如 `nps-svc-7d9c8f6b5-`），再用它去查 `involvedObject.kind=ReplicaSet` 的事件。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServicePodRefVO {

    /** `metadata.name`（用于查 `involvedObject.kind=Pod` 的事件）。 */
    private String name;

    /** `metadata.generateName`；**可能为 null**（非控制器创建的 pod）。 */
    private String generateName;
}
