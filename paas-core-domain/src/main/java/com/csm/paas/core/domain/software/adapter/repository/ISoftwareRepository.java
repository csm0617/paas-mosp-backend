package com.csm.paas.core.domain.software.adapter.repository;

import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.RelationRoleVO;

import java.util.List;

/** 软件目录仓储（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface ISoftwareRepository {

    /**
     * 软件列表（厂商 `SoftwareService.getSoftwareList:483-494`）。
     * <p>过滤 `name LIKE %?%` **或** `key LIKE %?%`，再按 `type` 等值过滤；
     * 排序**恒为 `order_top DESC` 在前**，其后按 `order`/`sort` 指定字段。
     *
     * @param likeName 已带 `%` 的模糊串；null 表示不过滤
     * @param order    允许 `key`(默认)/`name`/`createdAt`/`updatedAt`
     * @param asc      true=升序
     */
    List<SoftwareEntity> queryPage(String likeName, String type, String order, boolean asc, int offset, int size);

    long count(String likeName, String type);

    /** 是否已发布：白名单里存在路径 `/<key>/**`（厂商 `softWareReleaseStatus`，`:2878-2885`）。 */
    boolean existsWhiteListPath(String path);

    /** 软件关联的战位（厂商 `RoleMapper.getRelationRole`：`select id,name,key from g_role where id in (...)`）。 */
    List<RelationRoleVO> queryRelationRoles(List<String> roleIds);

    /** 该软件关联的战位 id（厂商按 `g_role_software.app_id = software.id` 查）。 */
    List<String> queryRoleIdsBySoftwareId(String softwareId);

    /**
     * 该软件的 **web 入口**服务端口（厂商 `SoftwareMapper.getWebEnter`）：
     * `select sp.id, sp.software_id, sp.service_name from g_service_port sp
     *  left join g_software_app sa on sa.software_id = sp.software_id
     *  where sp.software_id = ? and sp.web_entry = 1`
     * <p>返回 `serviceName`（可能为空）；无入口行时返回空列表。
     */
    List<String> queryWebEntryServiceNames(String softwareId);
}
