package com.csm.paas.core.domain.software.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 厂商 `ListenBackInfo{tcpListen, udpListen, ingressList}`（`POST /v1/config_map/listenBackup` 的响应体）。
 * <p>`ingressList` **厂商从不赋值** ⇒ 恒为 null（照抄）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ListenBackInfoEntity {

    private Map<String, Object> tcpListen;
    private Map<String, Object> udpListen;
    private Object ingressList;
}
