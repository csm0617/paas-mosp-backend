package com.csm.paas.core.domain.cicd.model.valobj;

import com.csm.paas.core.domain.cicd.model.entity.CicdEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/pub/cicdList` 的一行（厂商 `controller/response/cicd/CicdWithPodInfo extends Cicd`）。
 *
 * <p>厂商是**继承**：`CicdWithPodInfo` 的 JSON 是 `Cicd` 的 13 个字段 + `pod`。
 * 本仓 domain 不跨层继承（保持实体纯净），用"组合"表达同一份数据，由 trigger 层摊平成
 * 厂商的扁平 JSON。
 *
 * <p><b>`jobStatus` 是入库前的旧值</b>（照抄点，见 `ICicdService.list` 的说明）。
 * <p>`pod` **可能为 null**：厂商只在 `jobName != null` 时才 `setPod`（`CicdService:220-272`），
 * 所以 jobName 为空的历史行在 JSON 里是 `"pod":null`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdTaskItemVO {

    private CicdEntity cicd;
    private CicdJobPodVO pod;
}
