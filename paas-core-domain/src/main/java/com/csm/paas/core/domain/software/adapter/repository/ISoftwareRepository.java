package com.csm.paas.core.domain.software.adapter.repository;

import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.RelationRoleVO;
import com.csm.paas.core.domain.software.model.valobj.UploadStoreRecordVO;

import java.util.List;
import java.util.Optional;

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

    // ---------- 写（新增）----------

    boolean existsKey(String key);

    boolean existsName(String name);

    void insert(SoftwareEntity software);

    /**
     * 建立"软件 ↔ 战位"关联（厂商 `softwareRelateRole`，`SoftwareService.java:587-603`）：
     * 对每个战位，**已存在的关联跳过**，否则插入并把 `order` 设为"该战位已有软件数 + 1"。
     * <p>传空列表时**什么都不做**（厂商在列表为空时直接 return，不清理旧关联）。
     */
    void relateRoles(String softwareId, List<String> roleIds);

    // ---------- 放行 / 禁止放行（厂商 releaseSoftware，SoftwareService.java:2887-2931）----------

    boolean existsById(String softwareId);

    Optional<SoftwareEntity> queryById(String softwareId);

    /** 往白名单插入一条放行路径（`/<key>/**`）。 */
    void insertWhiteListPath(String path, long createdAt);

    /** 删掉该放行路径，返回删除行数（厂商据此判断"删除成功/失败"）。 */
    int deleteWhiteListPath(String path);

    // ---------- 删除（厂商 SoftwareController.removeSoftWare + SoftwareService.removeSoftware）----------

    /** 删 `g_role_software` 里该软件的关联（厂商 `deleteRoleSoftwareById` → `removeRoleSoftwareById`）。 */
    void deleteRoleSoftwareBySoftwareId(String softwareId);

    int deleteSoftwareAppBySoftwareId(String softwareId);

    /** 删 `g_service_port` 里该软件的端口行，返回删除行数（厂商据此决定要不要通知网关）。 */
    int deleteServicePortBySoftwareId(String softwareId);

    void deleteById(String softwareId);

    /**
     * 软件**当前版本**对应的上传记录（厂商 `UploadStoreRecordMapper.getCurrentSoftwareVersionInfo`）：
     * `select * from g_upload_store_record ur left join g_software s on ur.id = s.version where s.id = ?`
     * —— 即"上传记录 id 等于软件 version 列"的那一条；没有则返回 null。
     */
    UploadStoreRecordVO queryCurrentVersionRecord(String softwareId);
}
