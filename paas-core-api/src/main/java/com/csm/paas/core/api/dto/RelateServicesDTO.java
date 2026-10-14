package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `RelateServices`（`controller/form/software/RelateServices`）：`{list:[服务名]}`。
 * <p>用于 `POST`/`DELETE /v1/software/{id}/relate_service`。**字段名叫 list**（不是 serviceList）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RelateServicesDTO {

    private List<String> list;
}
