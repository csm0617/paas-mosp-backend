package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 影像域要看的那部分 Deployment（厂商 `K8sApiClientManager.getV1Deployments` 的裁剪版）。
 *
 * <p>厂商在两处读 Deployment，读的**不是同一层**注解，必须都保留：
 * <ul>
 *   <li>`getLoad`：读 `metadata.creationTimestamp`、`metadata.namespace`、
 *       以及 **pod 模板**注解 `spec.template.metadata.annotations["gaop/serviceLabel"]`；</li>
 *   <li>`getServiceRelationImage`：读 **Deployment 自身**的 `metadata.labels` 与 `metadata.annotations`
 *       （`ingress-lb`/`system/appType`/`displayName`/`system/appName`）。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageWorkloadVO {

    private String name;
    private String namespace;
    /** `spec.template.spec.containers[].image`，保持顺序。 */
    private List<String> containerImages;
    /** Deployment 自身 `metadata.labels`（可为 null）。 */
    private Map<String, String> labels;
    /** Deployment 自身 `metadata.annotations`（可为 null）。 */
    private Map<String, String> annotations;
    /** pod 模板 `spec.template.metadata.annotations`（可为 null）。 */
    private Map<String, String> templateAnnotations;
    /** `metadata.creationTimestamp` 的 epoch millis（可为 null）。 */
    private Long creationTimestamp;
}
