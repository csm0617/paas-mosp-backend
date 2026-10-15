package com.csm.paas.core.domain.software.service;

import com.csm.paas.core.domain.software.model.entity.SoftwareBackupExportEntity;

import java.util.List;

/**
 * 软件整包备份与还原（厂商 `SoftwareService.exportSoftwareForms:780-1020`、
 * `importSoftwareInfo:1195-1382`；对应 `SoftwareController` 的
 * `POST /v1/software/backup`、`POST /v1/software/recovery`、`POST /v1/software/import`）。
 *
 * <p>这里只做**领域装配**：K8s 的对象过滤/擦拭/落盘交给 {@code IServiceRuntimePort}，
 * GSON/Jackson 的 JSON 序列化交给 trigger（domain 无 JSON 库）。
 */
public interface ISoftwareBackupService {

    /**
     * 导出"软件 + 关联服务 + 战位 + 端口 + 白名单 + 服务名 i18n"的聚合
     * （厂商 `exportSoftwareForms:780-...`）。
     *
     * @param softwareIds 为空 ⇒ 厂商 type=1：导出**全部**软件，并把命名空间里全部（过滤后的）
     *                    Deployment/Service 一并带上；非空 ⇒ type=2：**先去重（LinkedHashSet）**，
     *                    只导出这些软件关联服务的 Deployment/Service
     */
    SoftwareBackupExportEntity exportSoftwareRelation(List<String> softwareIds);

    /**
     * 反向导入（厂商 `importSoftwareInfo:1195-1382`）：
     * <ol>
     *   <li>逐个 softwareForm：key 已存在 ⇒ 覆盖更新（含 `created_at = now`、`order_top = 0`）；
     *       否则新建（UUID 主键）；并写 `i18n.software.{en_US,fr_FR}.&lt;key&gt;`（只在 val 非 null 时写）；</li>
     *   <li>`deploymentList` 逐个 create-or-replace（镜像 host 换成当前 registry）；</li>
     *   <li>`serviceList` 逐个 create-or-replace；</li>
     *   <li>逐个软件：关联战位（按 roleKey 解析 id，解析不到的跳过）、关联服务
     *       （补 `g_software_app`、删多余关联）、还原端口行、按 `webEntry` 设服务入口；</li>
     *   <li>`whitelistPaths` 逐条"不存在才插"；`serviceKeyAndName` 逐条写 setting；</li>
     *   <li>最后通知网关刷新。</li>
     * </ol>
     */
    void importSoftwareRelation(SoftwareBackupExportEntity entity);
}
