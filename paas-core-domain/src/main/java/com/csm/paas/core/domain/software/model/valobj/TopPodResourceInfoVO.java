package com.csm.paas.core.domain.software.model.valobj;

import lombok.Builder;
import lombok.Data;

/**
 * 容器资源 Top5 的一条（厂商 `TopResourceList extends TopPodResourceInfo` 的并集：
 * **8 个键** `podName / cpuUsageRate / memory / networkOutBound / networkInBound /
 * diskRead / diskWrite / namespace`）。
 *
 * <p>厂商出处：`SoftwareService.topPodResource:1734-1873`。注意厂商先把榜单查出来的那一项
 * 填好，随后**无条件**把另外 5 个指标逐个补齐（查不到就写 **字符串 `"0"`**），
 * 最后 `BeanUtils.copyProperties(s, topPodResourceInfo)` ⇒ 响应对**任何 type** 都是这 8 个键。
 */
@Data
@Builder
public class TopPodResourceInfoVO {

    private String podName;
    private String cpuUsageRate;
    private String memory;
    private String networkOutBound;
    private String networkInBound;
    private String diskRead;
    private String diskWrite;
    private String namespace;
}
