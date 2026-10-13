package com.csm.paas.core.domain.role.service;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;

import java.util.List;
import java.util.Optional;

/** 战位查询服务（切片 3b：`/v1/role`、`/v1/role/list`、`/v1/role/switchRole`、`/v1/role/{id}`）。 */
public interface IRoleQueryService {

    RoleListEntity queryRoleList();

    /** 厂商 `GET /v1/role?username=`：按用户名过滤（非空时**不分页**，`total = list.size()`）。 */
    RoleListEntity queryRoleList(String username, int page, int size);

    /** 厂商 `GET /v1/role/list`：全量/按名模糊，**带 `userList`**（与 `/v1/role` 的唯一差别）。 */
    RoleListEntity queryAllRoleList(String roleName, int page, int size);

    /**
     * 厂商 `GET /v1/role/switchRole`：按 `g_role.type` 过滤（1=战位 2=角色），
     * 且 `softwareList`/`userList`/`subRoleList`/`onLineUserList` **四个集合都有值**。
     */
    RoleListEntity querySwitchRoleList(String roleName, int page, int size, int type);

    /** 厂商 `GET /v1/role/{id}`：纯按主键取，`nameFr`/`nameUs` 兜底为 `name`，四个集合全为 null。 */
    Optional<RoleEntity> queryDetail(String id);

    Optional<RoleEntity> queryByKey(String key);

    /** 战位名是否已存在（厂商 `GET /v1/role/exist_role_name`）。 */
    boolean existsByName(String roleName);

    /** 战位下的软件列表（厂商 `GET /v1/role/software`）。 */
    List<RoleSoftwareEntity> querySoftwareByRoleId(String roleId);
}
