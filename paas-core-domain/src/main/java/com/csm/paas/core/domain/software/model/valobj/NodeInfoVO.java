package com.csm.paas.core.domain.software.model.valobj;

import lombok.Builder;
import lombok.Data;

/**
 * 一个节点的最小视图（厂商 `schedulingNode:3096-3112` / `reStartBaseService:3321-3337`
 * 只用到 `metadata.name`、`metadata.labels` 与 `status.addresses[0].address`）。
 *
 * <p>厂商这两处遍历**全部**节点，并把所有 `metadata.name` 收进一个 `ipList`（名字与内容不符，
 * 照抄），同时把"最后一个带 `<nodeRole>` 标签的节点的**第一个 address**"当作 master IP。
 */
@Data
@Builder
public class NodeInfoVO {

    private String name;
    /** `metadata.labels`（可能为 null——厂商直接 `labels.containsKey(...)` ⇒ 其实会 NPE，见 deviations）。 */
    private java.util.Map<String, String> labels;
    /** `status.addresses[0].address`；没有地址时为 null。 */
    private String firstAddress;
}
