package com.csm.paas.core.domain.role.service;

import com.csm.paas.core.domain.role.model.entity.RoleListEntity;

/** 战位查询服务（对应厂商 GET /v1/role 与 /v1/role/list）。 */
public interface IRoleQueryService {

    RoleListEntity queryRoleList();
}
