package com.csm.paas.core.api.dto;

import lombok.Data;

import java.util.Date;

/**
 * 厂商 `POST /v1/service/logs` 的请求体（厂商 `ServiceLogForm` → `LogForm`）。
 *
 * <p>厂商出处：`ServiceController.serviceLogs:3931-3947`；字段被
 * `BeanUtils.copyProperties(form, logForm)` 拷进 `LogForm` 后交给 `getHistoryLog`。
 */
@Data
public class ServiceLogFormDTO {

    private String serviceName;
    /** 为空 ⇒ 厂商取**当前时间**。 */
    private Date startTime;
    /** 为空 ⇒ 厂商取**当前时间**。 */
    private Date endTime;
    /** 为空或 &lt;0 ⇒ 厂商取 **1000**（Loki 的 `limit`）。 */
    private Integer size;
    /** 为空 ⇒ 厂商取**空串**（`|=` 行过滤关键字）。 */
    private String keyword;
}
