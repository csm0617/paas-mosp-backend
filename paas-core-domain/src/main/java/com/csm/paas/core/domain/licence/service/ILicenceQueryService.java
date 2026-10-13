package com.csm.paas.core.domain.licence.service;

import com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity;

/** 授权查询（厂商 GET /v1/licence 与 /v1/licence/info）。 */
public interface ILicenceQueryService {

    LicenceInfoEntity queryInfo();

    /**
     * 厂商 `GET /v1/licence` 的 `code`：`Base64(原始机器码 + "," + "gaop" + ",")`——
     * **注意尾部多一个逗号**（厂商 `LicenceService.getMachineCodeBase64()`，`:161-190`）。
     */
    String machineCode();

    /**
     * 软件授权（软件详情里的 `licence.code` 前缀）：`Base64(原始机器码 + "," + "gaop")`——
     * **没有尾部逗号**（厂商 `LicenceService.getMachineCodeBase64ByService()`，`:327-356`）。
     * <p>两个方法就差这一个逗号，实测确认（见 `docs/findings/41` 的更正节）。
     */
    String machineCodeForSoftware();

    /**
     * 厂商 `GET /v1/licence/checkAndActiveGis`：GIS 授权检查。
     * <p>本环境的厂商实现返回 false；GIS 授权（在线激活 GIS）属 gaop-auth 域，这里先如实返回 false。
     */
    boolean checkAndActiveGis();

    /**
     * 平台激活码校验（厂商 `LicenceService.checkActiveCode:633-680`）：
     * 用 `g_activate['gaop'].public_key`（RSA512）验签，**issuer 必须是 `gaop`**；
     * 取 `machineCode` claim 拆成集合，与**本机机器码里的 gaopCode 集合求交集**，
     * 无交集 → 400 `auth.invalid.auth.machine.id`。
     *
     * <p>异常契约照抄：验签失败 → `licence.invalid.key.signature`；已过期 → `licence.activation.code.expired`；
     * claim 非法 → `licence.invalid.args`（带异常消息）。
     * <p>成功时给 4 个字段：`end`=exp、`code`=传入的机器码、`expired`=now&gt;exp、
     * `endIso8601`=**系统时区**的 ISO 时间（`LocalDateTime.ofInstant(..., systemDefault())`）。
     */
    LicenceInfoEntity checkActiveCode(String machineCodeBase64, String activeCode);

    /**
     * 软件授权码校验（厂商 `LicenceService.checkSoftwareActiveCode:581-631`）：
     * 用 `g_auth_info['default_auth']` 最新一条的 `public_key`（RSA512）验签，issuer `gaop`；
     * 取 `serviceIdList` claim，**包含传入的 softwareKey 才算通过**，否则返回 `LicenceInfo.expired(机器码)`。
     *
     * <p>这个方法的异常契约与平台版**不同**：验签失败 → `licence.invalid.signature`（注意不是 `.key.signature`）；
     * 过期 → `licence.activation.code.expired`；claim 非法 → `licence.invalid.args`；
     * 其它异常 → **返回 null**（调用方 `getSoftwareLicenceInfo` 把 null 当"未激活"）。
     */
    LicenceInfoEntity checkSoftwareActiveCode(String machineCodeBase64, String activeCode, String softwareKey);
}
