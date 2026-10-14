package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** VIP 条目；照抄厂商 `service/vip/VipItem.java:8-12`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VipItemVO {

    private String interfaceName;
    private String vip;
}
