package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 厂商 ServicesItem.mappingInfo 的键名（逐字对齐运行时样本：三键全 null）。
 * <p>前端 `p__ServiceManage` 用它打开映射面板，因此**必须是对象而不是 null**。
 */
@Data
@Builder
public class MappingInfoDTO {
    private Object intranetInfo;
    private Object nodePortInfo;
    private Object ingressInfo;
}
