package com.csm.paas.core.domain.role.adapter.repository;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;

import java.util.List;

/** 战位的本地仓储接口（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface IRoleRepository {

    List<RoleEntity> queryOrderByCreatedAtDesc();

    long count();

    List<RoleSoftwareEntity> querySoftwareByRoleId(String roleId);
}
