package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个服务的运行状态（厂商 `controller/response/ServiceStatus{serviceName,status}`，
 * 用于 `GET /v1/pub/service_status`）。
 *
 * <p>`status` 的取值来自厂商 `utils/TenxUtils.getStatus`（`TenxUtils.java:49-74`）：
 * `STOP` / `RUNNING` / **`STARING`**（厂商把 STARTING 拼错了，照抄——`TenxUtils.java:70`、`:74`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubServiceStatusVO {

    private String serviceName;
    private String status;
}
