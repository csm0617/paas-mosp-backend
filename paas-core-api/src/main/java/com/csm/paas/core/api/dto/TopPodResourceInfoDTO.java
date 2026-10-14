package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 厂商 `POST /v1/software/top` 的响应项（厂商 `controller/response/TopPodResourceInfo`）。
 *
 * <p>8 个键**全是 String**（厂商连数值也是字符串，来自 Prometheus 的 `value[1]`）；
 * 未取到的指标是字符串 **`"0"`**，不是 null。厂商出处：`SoftwareService.topPodResource:1734-1873`。
 * <p>注：厂商返回的类型其实是 `TopResourceList extends TopPodResourceInfo`（多一个 `namespace`），
 * 最终 `BeanUtils.copyProperties` 后对外就是这 8 个键。
 */
@Data
@Builder
public class TopPodResourceInfoDTO {

    private String podName;
    private String cpuUsageRate;
    private String memory;
    private String networkOutBound;
    private String networkInBound;
    private String diskRead;
    private String diskWrite;
    private String namespace;
}
