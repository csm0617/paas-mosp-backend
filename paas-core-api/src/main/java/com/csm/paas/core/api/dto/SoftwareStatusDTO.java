package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `SoftwareStatus`（`controller/response/SoftwareStatus`）：**只有一个 `status` 键**。
 * <p>用于 `GET /v1/software/{id}/start|stop|status`。
 * <p>取值：`STOP` / `RUNNING`（软件总状态还有 `PART_RUN`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareStatusDTO {

    private String status;
}
