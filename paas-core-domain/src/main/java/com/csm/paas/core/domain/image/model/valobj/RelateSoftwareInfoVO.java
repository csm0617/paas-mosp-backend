package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 镜像被哪个服务使用（厂商 `controller/response/RelateSoftwareInfo`）。
 *
 * <p><b>两个键都是 camelCase</b>（厂商 `@JsonProperty("serviceName")`/`("serviceKey")`），
 * 与同响应里其他 snake_case 键不同——照抄。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RelateSoftwareInfoVO {

    /** 取自 Deployment **pod 模板注解** `gaop/serviceLabel`（取不到就不设）。 */
    private String serviceName;
    /** 取自 Deployment 名。 */
    private String serviceKey;
}
