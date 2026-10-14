package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * `POST /v1/config_map/importListen` 的入参（厂商 `ListenBackInfo` 的两个 ConfigMap 字段，
 * `SoftwareService.importListenConfig:3593` 的 `JsonConfig.JACKSON.readValue(in, ListenBackInfo.class)`）。
 *
 * <p>域层只拿**原样的 ConfigMap 快照 Map**（JSON 解析在 trigger）；`ingressList` 字段本次用不到，
 * 故不搬。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ListenImportVO {

    /** JSON 里的 `tcpListen`（一个完整 ConfigMap 对象）；没有该键 ⇒ null。 */
    private Map<String, Object> tcpListen;

    /** JSON 里的 `udpListen`（一个完整 ConfigMap 对象）；没有该键 ⇒ null。 */
    private Map<String, Object> udpListen;
}
