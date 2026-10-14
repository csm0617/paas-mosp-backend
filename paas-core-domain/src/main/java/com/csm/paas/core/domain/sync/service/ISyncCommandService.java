package com.csm.paas.core.domain.sync.service;

import com.csm.paas.core.domain.sync.model.valobj.SyncParamVO;

/**
 * 数据同步写操作（厂商 `SyncController` 的 `POST /v1/sync` 与 `GET /v1/sync/database/exist`）。
 */
public interface ISyncCommandService {

    /**
     * 开始一次同步（厂商 `SyncController.syncData:258-467`）。
     *
     * <p>照抄的骨架（外部数据搬运部分见类文档与报告里的"未实现"清单）：
     * <ol>
     *   <li>`syncStatus.isSyncing()` ⇒ 重发一条进度通知后**直接返回 true**；</li>
     *   <li>baseUrl 归一化：非 `^http[s]?://` ⇒ `finalUrl = "http://" + baseUrl + "/api/gaop/"`、
     *       `imageUrl = "http://" + baseUrl`；`finalUrl` 不以 `/` 结尾则补 `/`；</li>
     *   <li>`method = param.sync`；flags 来自 `data` 集合；</li>
     *   <li>逐类型从对端拉数据（失败 ⇒ 置 `success=false/syncing=false` 后抛厂商对应的 i18n 键）；</li>
     *   <li>`setTotal(...)` —— **厂商这里对未拉用户的场景会 NPE**（`getUserSync()` 为 null），照抄；</li>
     *   <li>起一个**裸线程**做导入 + 组合同步，线程末尾把状态置为成功并推一次，
     *       然后**立刻复位**（`success=false/current=0`）——所以 `GET /status` 事后看到的又是初始态；</li>
     *   <li>立即返回 `true`。</li>
     * </ol>
     */
    boolean startSync(SyncParamVO param);

    /**
     * `GET /v1/sync/database/exist?url=`（厂商 `SyncController.existDatabaseFile:1339-1361`）：
     * 打对端 `http://<url>/api/gaop/v1/sync/database/status`，10 秒超时，
     * 读响应 `data` 布尔值；**任何异常/非 2xx ⇒ false**。
     */
    boolean databaseExistsOnPeer(String url);
}
