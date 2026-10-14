package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个卷挂载（厂商在 `updateServiceVolumes` 里现造的 `V1VolumeMount`）。
 * <p>规格见 `docs/findings/119`：configMap 卷用 `{mountPath = dir + "/" + key, subPath = key}`，
 * hostPath 卷用 `{mountPath = podPath}`，且**只有 `readOnly == 2` 才置 true**（其它值连 false 都不写）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VolumeMountVO {

    private String name;
    private String mountPath;
    /** configMap 卷才有；hostPath 卷为 null。 */
    private String subPath;
    /** 厂商：只有 `readOnly == 2` 才写 true，否则**整个字段不写**（所以是 null 而不是 false）。 */
    private Boolean readOnly;
}
