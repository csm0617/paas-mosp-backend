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

    /**
     * `g_activate` 里 `name='gaop'` 那行的 `public_key`（base64 X.509 RSA）——
     * 厂商 `LicenceService.get():531-545`，用于校验**平台激活码**。
     */
    java.util.Optional<String> queryGaopActivatePublicKey();

    /**
     * `g_auth_info` 里 `jar_name='default_auth'` 的**最新一条**（按 `create_at` 降序）的 `public_key` ——
     * 厂商 `checkSoftwareActiveCode:584-585`/`getPublicKeyAlgorithm:567-579`，用于校验**软件授权码**。
     */
    java.util.Optional<String> queryDefaultAuthPublicKey();

    /**
     * 落库激活码（厂商 `LicenceService.saveActiveCode:467-`）：`g_setting['system.active.code']`
     * **有则更新、无则插入**。厂商写完之后还会往授权文件写一份（本实现未做文件，记入 findings）。
     */
    void saveActiveCode(String activeCode);

    /** 清空激活码（厂商 `clearActiveCode:448-460`）：删 `g_setting['system.active.code']`（并删授权文件，本实现未做文件）。 */
    void clearActiveCode();
}
