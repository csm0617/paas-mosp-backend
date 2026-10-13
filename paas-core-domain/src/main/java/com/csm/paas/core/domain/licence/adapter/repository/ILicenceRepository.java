package com.csm.paas.core.domain.licence.adapter.repository;

import java.util.Optional;

/**
 * 授权仓储：激活码与试用期都存在 `g_setting` 里（厂商 `LicenceService.getActiveCode()`/`getLicenceInfo()` 同源）。
 * <p>厂商还会优先读授权文件（`licenceFile`），本实现暂只覆盖 `g_setting` 这条路径。
 */
public interface ILicenceRepository {

    Optional<String> queryActiveCode();

    Optional<String> querySettingValue(String key);

    void saveSettingValue(String key, String val, String desc);

    /**
     * **原始机器码**（厂商 `LicenceService.machineCode`，配置项 `machine.code`）——**不是** base64，
     * 也不含 platformId。厂商的推导顺序：配置值 → 若 `/data/licenceInfo/machine_codes.json` 有内容则用它
     * （实测该文件在本环境**不存在**）。base64 与拼接在领域服务里做（两条路径拼法不同，见 `ILicenceQueryService`）。
     */
    String rawMachineCode();
}
