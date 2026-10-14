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

    /**
     * 战位下的软件列表（厂商 `GET /v1/role/{roleId}/software` → `RoleService.roleSoftwareList:416-...`）。
     *
     * <p>分页 + **按 `order` 升序**；每行还会按软件类型补 `appName`（i18n 名）与 `url`：
     * `CS` 类型拼 `gaop://cs?...`（含请求头 `x-forwarded-host`），非 CS 用 `routerKey`。
     * <p>**当前实现只做到"查库 + 分页 + 排序"**：`appName` 的 i18n 解析与 `url` 的两种拼法尚未实现
     * （库里 `g_role_software` 为空，无法用真实数据对拉这两段）——已记在 `findings/90`。
     */
    com.csm.paas.core.domain.role.model.entity.RoleSoftwareListEntity querySoftwareList(
            String roleId, int page, int size);

    /**
     * 导出/备份用行数据（厂商 `RoleService.generateExportExcel:784-830`，规格 `findings/157`）。
     *
     * <ul>
     *   <li>`ids` 为空/null ⇒ **全量**（厂商 `isInWhenPresent` 空集合不加 where）；按 `created_at DESC`；</li>
     *   <li>**排除 `admin`/`dev`/`user` 三个内置战位**（厂商 stream filter）；</li>
     *   <li>三个列表列（关联应用/子角色/用户）是各自的 **`key`/用户名** 列表，由调用方序列化成 JSON 写单元格；
     *       空列表 ⇒ 调用方写空单元格（厂商只有非空才 `gson.toJson`）；</li>
     *   <li>`nameUs`/`nameFr` 来自 `g_setting` 自动名（键 `i18n.role.<lang>.<key>`），取不到就是 **null**。</li>
     * </ul>
     */
    java.util.List<com.csm.paas.core.domain.role.model.valobj.RoleExportRowVO> queryExportRows(
            java.util.List<String> ids);
}
