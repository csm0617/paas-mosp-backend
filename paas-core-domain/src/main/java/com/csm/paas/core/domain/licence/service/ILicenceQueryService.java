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
}
