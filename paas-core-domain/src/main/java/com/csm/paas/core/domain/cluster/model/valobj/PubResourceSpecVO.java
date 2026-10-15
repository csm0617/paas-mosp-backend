package com.csm.paas.core.domain.cluster.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/pub/patchResource` 的入参（厂商 `controller/form/ResourceForm.java` +
 * `LimitInfo.java` + `ResourceInfo.java`）。
 *
 * <p>厂商结构是嵌套的 `{name, limits:{cpu,memory,gpu}, requests:{cpu,memory}}`；
 * domain 用扁平的 VO 承载，trigger 层负责摊平/还原（保证 JSON 契约不变）。
 *
 * <p>照抄点：`gpu` 只在 **limits** 里有，`requests` 没有 gpu 字段（`FileService:1157-1160`、
 * `:1204-1207` 两处都是 `if (limit.getGpu() != null)`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubResourceSpecVO {

    private String name;

    /** `limits.cpu`。 */
    private String limitCpu;

    /** `limits.memory`。 */
    private String limitMemory;

    /** `limits.gpu`（可空；非空时写 `nvidia.com/gpu`）。 */
    private String limitGpu;

    /** `requests.cpu`。 */
    private String requestCpu;

    /** `requests.memory`。 */
    private String requestMemory;
}
