package com.csm.paas.core.domain.licence.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 软件激活码里解析出的载荷（厂商 `authSoftware:3245-3257` 用到的三个 claim）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareActivationPayloadVO {

    /** `machineCode` claim（为空 → `auth.missing.machine.id.signature`）。 */
    private String machineCode;
    /** `serviceIdList` claim（为空 → `auth.missing.software.key.signature`）。 */
    private List<String> serviceIdList;
    /** JWT 的 `exp`（毫秒，**只到秒**）。 */
    private Long expiresAt;
}
