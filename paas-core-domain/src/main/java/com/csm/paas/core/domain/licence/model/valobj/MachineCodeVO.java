package com.csm.paas.core.domain.licence.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 机器码的解析结果（厂商 `LicenceKey.parseMachineCode`，`constant/LicenceKey.java:19-38`）。
 * <p>规则：**以 `ey` 开头则直接返回空对象**（`ey` 是 JWT 的 base64 前缀，厂商用它区分"传进来的其实是激活码"）；
 * 否则 base64 解码后按 `,` 切分，依次取 `gaopCode`/`tenxCode`/`gisCode`（都 trim，空则留 null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MachineCodeVO {

    private String gaopCode;
    private String tenxCode;
    private String gisCode;

    public static MachineCodeVO parse(String machineCode) {
        MachineCodeVO result = new MachineCodeVO();
        if (machineCode == null) {
            return result;
        }
        if (machineCode.startsWith("ey")) {
            return result;
        }
        String decoded = new String(Base64.getDecoder().decode(machineCode), StandardCharsets.UTF_8);
        String[] split = decoded.split(",");
        if (split.length > 0 && !split[0].trim().isEmpty()) {
            result.setGaopCode(split[0].trim());
        }
        if (split.length > 1 && !split[1].trim().isEmpty()) {
            result.setTenxCode(split[1].trim());
        }
        if (split.length > 2 && !split[2].trim().isEmpty()) {
            result.setGisCode(split[2].trim());
        }
        return result;
    }
}
