package com.csm.paas.core.domain.role.service;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;

import java.util.List;
import java.util.Optional;

/** 战位查询服务（对应厂商 GET /v1/role 与 /v1/role/list）。 */
public interface IRoleQueryService {

    RoleListEntity queryRoleList();

    Optional<RoleEntity> queryByKey(String key);

    /** 战位下的软件列表（厂商 `GET /v1/role/software`）。 */
    List<RoleSoftwareEntity> querySoftwareByRoleId(String roleId);
}
