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

    /** 机器码：环境相关（厂商由配置文件+本机文件推导），这里由配置提供。 */
    String machineCode();
}
