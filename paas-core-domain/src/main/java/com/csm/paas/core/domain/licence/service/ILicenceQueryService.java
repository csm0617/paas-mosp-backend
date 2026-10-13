package com.csm.paas.core.domain.licence.service;

import com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity;

/** 授权查询（厂商 GET /v1/licence 与 /v1/licence/info）。 */
public interface ILicenceQueryService {

    LicenceInfoEntity queryInfo();

    String machineCode();

    /**
     * 厂商 `GET /v1/licence/checkAndActiveGis`：GIS 授权检查。
     * <p>本环境的厂商实现返回 false；GIS 授权（在线激活 GIS）属 gaop-auth 域，这里先如实返回 false。
     */
    boolean checkAndActiveGis();
}
