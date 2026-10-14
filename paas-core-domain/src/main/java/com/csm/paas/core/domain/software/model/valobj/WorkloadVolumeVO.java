package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一个卷（厂商在 `updateServiceVolumes` 里现造的 `V1Volume`）。
 * <p>`configMapName` 非空 ⇒ configMap 卷（`items` 生效）；`hostPath` 非空 ⇒ hostPath 卷（`hostPathType` 生效）。
 * <p>规格见 `docs/findings/119`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkloadVolumeVO {

    /** 卷名（`tenxcloud-time-localtime` 固定；否则 `<configmap|hostpath>-volume-<5 位随机>`）。 */
    private String name;

    /** configMap 卷的来源 configmap 名；hostPath 卷为 null。 */
    private String configMapName;

    /** configMap 卷的 `items`：`{key, path}`——注意厂商的 `path` 用的是 **key**（不是 dir）。 */
    private List<KeyToPathVO> items;

    /** hostPath 卷的宿主路径。 */
    private String hostPath;

    /** hostPath 卷的 `type`（厂商恒为空串 `""`）。 */
    private String hostPathType;

    /** configMap 卷的 `items` 元素（厂商 `V1KeyToPath`）。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KeyToPathVO {
        private String key;
        private String path;
    }
}
