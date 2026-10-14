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

    /** 删**一对**关联（厂商 `updateSoftwareRelateRole` 里逐条删 `role_id + app_id`）。 */
    void deleteRoleSoftwareRelation(String roleId, String softwareId);

    int deleteSoftwareAppBySoftwareId(String softwareId);

    /** 删 `g_service_port` 里该软件的端口行，返回删除行数（厂商据此决定要不要通知网关）。 */
    int deleteServicePortBySoftwareId(String softwareId);

    void deleteById(String softwareId);

    /** 除自己之外是否已存在该 key（厂商 `duplicateKey`，软件域）。 */
    boolean existsByKeyExcludingId(String key, String id);

    /** 除自己之外是否已存在该 name（厂商 `duplicateName`，软件域）。 */
    boolean existsByNameExcludingId(String name, String id);

    /** 整行更新（厂商 `updateByPrimaryKey`，**不是** selective）。 */
    void update(SoftwareEntity software);

    /**
     * 软件**当前版本**对应的上传记录（厂商 `UploadStoreRecordMapper.getCurrentSoftwareVersionInfo`）：
     * `select * from g_upload_store_record ur left join g_software s on ur.id = s.version where s.id = ?`
     * —— 即"上传记录 id 等于软件 version 列"的那一条；没有则返回 null。
     */
    UploadStoreRecordVO queryCurrentVersionRecord(String softwareId);

    /** 授权列表分页：name 模糊 / type 等值，**只按 `created_at DESC`**（厂商 `getSoftwareAuthList`）。 */
    List<SoftwareEntity> queryAuthPage(String likeName, String type, int offset, int size);

    /** 授权列表总数（**与 `queryAuthPage` 同一套 WHERE**，name 只打在 name 上）。 */
    long countAuth(String likeName, String type);

    /** 软件在某服务下的端口映射（厂商 `servicePortList`，**不排序**）。 */
    List<com.csm.paas.core.domain.software.model.valobj.ServicePortVO> queryServicePorts(
            String softwareId, String serviceName);

    /** 同一软件+服务下该路径是否已被占用（厂商 `isServicePathExist`）。 */
    boolean existsServicePath(String softwareId, String serviceName, String pathName);

    /** 按 软件+服务+端口 取；取不到返回 null（厂商据此抛 `service.port.not.found`）。 */
    com.csm.paas.core.domain.software.model.valobj.ServicePortVO queryServicePort(
            String softwareId, String serviceName, int port);

    /** 更新该行的 `path_name`，返回**重新查出来的**那一行（厂商返回 `selectByPrimaryKey(...)` 的结果）。 */
    com.csm.paas.core.domain.software.model.valobj.ServicePortVO updateServicePortPath(long id, String pathName);

    /** 清空该软件的授权字段（`active_code`/`active_create`/`active_expire` → null，`updated_at` → 传入值）。 */
    void clearSoftwareAuth(String softwareId, long updatedAt);

    /** 写入授权（`active_create`/`active_code`/`active_expire`；**不动 `updated_at`**，照抄厂商）。 */
    void saveSoftwareAuth(String softwareId, long activeCreate, String activeCode, long activeExpire);

    /** 某服务的 Pod 重启记录（厂商 `GET /v1/service/restartInfo`，按 `create_at` 倒序）。 */
    List<com.csm.paas.core.domain.software.model.valobj.PodRestartInfoVO> queryPodRestartInfo(String serviceName);

    /** 某软件下所有服务的路径名（厂商 `allServicePathBySoftware`，三表 join）。 */
    List<String> queryServicePathsBySoftware(String softwareId);

    // ---------- patchService 的 B 分支（厂商 updateServiceDiscovery:1107-1136）----------

    /** `g_software_app` 里 `app_name` 命中的软件 id 列表（厂商 `where app_name = ?` 再取 `softwareId`）。 */
    List<String> querySoftwareIdsByAppName(String appName);

    /**
     * 某软件关联的全部 `app_name`（厂商 `softwareAllService:2219-2221`：`where software_id = ?`，**无 orderBy**）。
     * <p>用于 `GET /v1/software/relationService/{id}` 过滤服务列表。
     */
    List<String> queryAppNamesBySoftwareId(String softwareId);

    /**
     * 把 `g_software.updated_at` 推进到给定值（厂商 `updateSoftwareTimeByService:3776-3788`：
     * 按 `g_software_app.app_name` 找到关联软件，逐个 `setUpdatedAt(now)` 后更新）。
     * <p>`PUT /v1/service/rollingUpdate` 在 patch 成功**之后**调它。
     */
    void updateSoftwareUpdatedAt(String softwareId, long updatedAt);

    // ---------- batch-restart（厂商 serviceRestart:3826-3912）----------

    /** 只更新 `order_top` 与 `order_time`（厂商 `orderSoftWare`）。 */
    void updateSoftwareOrderTop(String softwareId, Integer orderTop, long orderTime);

    /** 只更新 `icon` 与 `updated_at`（厂商 `updateIcon`）。 */
    void updateSoftwareIcon(String softwareId, String icon, long updatedAt);

    /** 只更新 `screenshot` 与 `updated_at`（厂商 `updateScreenshot`）。 */
    void updateSoftwareScreenshot(String softwareId, String screenshot, long updatedAt);

    /** 全部软件行（厂商 `getOverViewInfo` 里的 `select(allRows())`）。 */
    List<SoftwareEntity> queryAllSoftware();

    /** 某服务+某 pod 已有的重启次数（厂商用它算 `restartNum = max(1, 历史最大值)`）。 */
    List<Integer> queryPodEventRestartCounts(String serviceName, String podName);

    /** 写一条重启记录（厂商 `podEventMapper.insertSelective`）。 */
    void insertPodEvent(String serviceName, String podName, long createAt, int restartCount);

    /** 该软件+服务下 `web_entry = true` 那一行的 port；没有则 empty（厂商 `softwareServicePort`）。 */
    java.util.Optional<Integer> queryWebEntryPort(String softwareId, String serviceName);

    /** 插一行端口映射（**不写 `allow`**，与厂商 `insertSelective` 一致）。 */
    void insertServicePortRow(String softwareId, String serviceName, int port, String pathName,
                              boolean webEntry, long updatedAt);

    /** 删某软件在某服务下的全部端口映射（厂商 `where software_id = ? and service_name = ?`）。 */
    void deleteServicePorts(String softwareId, String serviceName);

    /**
     * 批量插端口映射（厂商 `insertBatch`）。**空列表由调用方拦下**——厂商的 `<foreach>` 对空列表会拼出
     * 非法 SQL 并报 400，我们在领域层显式抛同一个键（见 `findings/118` §3）。
     */
    void insertServicePorts(List<com.csm.paas.core.domain.software.model.valobj.ServicePortVO> rows);
}
