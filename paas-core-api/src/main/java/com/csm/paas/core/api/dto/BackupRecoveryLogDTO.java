package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 备份/还原日志（厂商 {@code controller/websocket/model/BackupRecoveryLog.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/websocket/model/BackupRecoveryLog.java:9-16`。
 * <p>照抄点：厂商**没有 @Data**，只有手写 getter/setter（Lombok {@code @Builder} 生成的构造器是包级私有，
 * 但 Jackson 走 getter，序列化不受影响）；`timestamp` 是**基本类型 long**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupRecoveryLogDTO {

    private String type;
    private String module;
    private String message;
    private String data;
    private long timestamp;
}
