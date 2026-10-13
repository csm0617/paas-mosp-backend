package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `LicenceInfo`（**4 键**）：`{end, endIso8601, code, expired}`。
 * <p><b>与 `/v1/licence/info` 的 `LicenceInfoDTO`（7 键）不是同一个形状</b>——后者多
 * `authStatus`/`currentTime`/`trialPeriod`。这是本仓第三次遇到"同名不同形"，故拆成两个 DTO。
 * <p>未激活/校验失败时厂商给的是 `LicenceInfo.expired(code)` = `{end:-1, endIso8601:null, code, expired:true}`
 * （`endIso8601` 不设，故为 null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareLicenceDTO {

    private long end;
    private String endIso8601;
    private String code;
    private boolean expired;
}
