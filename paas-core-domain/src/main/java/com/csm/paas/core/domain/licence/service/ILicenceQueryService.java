package com.csm.paas.core.domain.licence.service;

import com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity;

/** 授权查询（厂商 GET /v1/licence 与 /v1/licence/info）。 */
public interface ILicenceQueryService {

    LicenceInfoEntity queryInfo();

    String machineCode();
}
