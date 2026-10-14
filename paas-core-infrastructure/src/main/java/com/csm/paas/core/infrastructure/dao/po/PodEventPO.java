package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/**
 * `g_pod_event` 的投影（只取重启记录需要的三列）。
 * <p>**用 PO 不用 Map**：H2 的列标签是大写、MySQL 是小写，Map 取键会踩大小写（本会话已踩过两次）。
 */
@Data
public class PodEventPO {

    private String podName;
    private Integer restartCount;
    private Long createAt;
}
