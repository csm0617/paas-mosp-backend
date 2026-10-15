package com.csm.paas.core.domain.software.adapter.repository;

import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareBackupPortVO;

import java.util.List;
import java.util.Optional;

/**
 * 软件整包备份/还原的仓储（厂商 `SoftwareService.exportSoftwareForms:780-1020` 与
 * `importSoftwareInfo:1195-1382` 用到的库访问）。
 *
 * <p>与 `ISoftwareRepository` 分开：这里是一次性备份/还原所需的批量/特殊查询，
 * 不动既有仓储（既有实现有在线对拉口径，改动风险高）。
 */
public interface ISoftwareBackupRepository {

    /** 按 id 批量取（厂商 `softwareMapper.select(where id in (...))`）。 */
    List<SoftwareEntity> queryByIds(List<String> ids);

    /** 全部软件（厂商 `select(allRows())`）。 */
    List<SoftwareEntity> queryAll();

    /** 按 key 取单个（厂商 `selectOne(where key = ?)`）。 */
    Optional<SoftwareEntity> queryByKey(String key);

    /**
     * 该软件关联的战位 **key** 列表（厂商 `roleMapper` 与 `g_role_software` 的左连接后取 `role.key`）。
     * <p>与 `ISoftwareRepository.queryRoleIdsBySoftwareId`（返回 id）不同，导出用 key。
     */
    List<String> queryRoleKeysBySoftwareId(String softwareId);

    /** 该软件全部端口行（厂商 `getSoftwareServicePort`）。 */
    List<SoftwareBackupPortVO> queryServicePorts(String softwareId);

    /** 该软件 `web_entry = true` 的第一行（厂商 `serviceWebEntry.get(0)`）；没有返回 empty。 */
    Optional<SoftwareBackupPortVO> queryFirstWebEntry(String softwareId);

    /** 插一行软件↔服务关联（厂商 `softwareAppMapper.insertSelective`）。 */
    void insertSoftwareApp(String softwareId, String appName, long createdAt);

    /** 删该软件下这些服务名的关联（厂商 `app_name in (...)`）。 */
    void deleteSoftwareAppsByNames(String softwareId, List<String> names);

    /**
     * 还原端口行：按 软件+服务+端口 判存在，不存在则插（`allow` 留 NULL），
     * 存在则只改 `path_name`/`updated_at`（厂商 `importSoftwareInfo` 里
     * `insertOrUpdateServicePortByExportForm` 的净效果）。
     */
    void upsertServicePort(String softwareId, String serviceName, int port, String pathName,
                           Boolean webEntry, long updatedAt);

    /**
     * 厂商 `importSoftwareInfo` 对**已存在 key** 的软件走 `updateByPrimaryKey`：整行覆盖，
     * **包含 `created_at`**（既有 `ISoftwareRepository.update` 刻意不写这一列）。
     */
    void updateSoftwareForImport(SoftwareEntity software);
}
