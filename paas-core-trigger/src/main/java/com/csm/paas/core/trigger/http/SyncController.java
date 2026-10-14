package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.SyncParamDTO;
import com.csm.paas.core.api.dto.SyncStatusDTO;
import com.csm.paas.core.domain.sync.model.valobj.SyncParamVO;
import com.csm.paas.core.domain.sync.service.ISyncCommandService;
import com.csm.paas.core.domain.sync.service.ISyncQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `SyncController`（`/v1/sync`）。
 *
 * <p>已实现：`GET /status`（`findings/153`）、`POST /`（真同步的**状态机 + 对端拉取**，
 * 导入侧只落地 `configmap`/`network` 两处，其余显式记日志未实现——见
 * {@code SyncCommandServiceImpl} 的类文档）、`GET /database/exist`。
 *
 * <p>未实现：`/base_url`(GET/PUT)、`/{roleId}/users`、`/user`、`/role`、`/subRole`、`/software`、
 * `/service`、`/configMap`、`/client` 等**对端拉取端点**（它们只在两台 GAOP 节点互相同步时被调用）。
 */
@RestController
@RequestMapping("/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final ISyncQueryService syncQueryService;
    private final ISyncCommandService syncCommandService;

    /**
     * 厂商 `GET /v1/sync/status`：当前同步状态（厂商直接返回 static 单例）。
     *
     * <p>`POST /v1/sync` 现在也读写**同一个单例**（`SyncStatusHolder`），所以同步中能看到进度；
     * 注意厂商在线程末尾**推完就复位**，事后看到的仍是初始态（照抄）。
     */
    @GetMapping("/status")
    public ResponseDTO<SyncStatusDTO> getSyncStatus() {
        var status = syncQueryService.queryStatus();
        return ResponseDTO.ok(SyncStatusDTO.builder()
                .syncing(status.isSyncing()).success(status.isSuccess())
                .currentSyncName(status.getCurrentSyncName()).currentSyncType(status.getCurrentSyncType())
                .current(status.getCurrent()).total(status.getTotal())
                .errorStatus(status.getErrorStatus())
                .build());
    }

    /**
     * 厂商 `POST /v1/sync`（`SyncController.java:258-467`）：从对端节点同步数据。
     *
     * <p>body `{baseUrl, sync, data, userName}`；**立即返回 `true`**，真正的搬运在后台裸线程里。
     * 照抄的怪点：① 正在同步时只重发一条进度并返回 true；② baseUrl 非 http 时补
     * `http://…/api/gaop/`；③ `data` 里没有 `user` 时 `setTotal(...)` 会 **NPE ⇒ 500**
     * （且此时 `syncing` 已被置 true）；④ 结束前把状态**复位**。
     */
    @PostMapping
    public ResponseDTO<Boolean> syncData(@RequestBody SyncParamDTO param) {
        SyncParamVO vo = SyncParamVO.builder()
                .baseUrl(param.getBaseUrl())
                .sync(param.getSync())
                .data(param.getData())
                .userName(param.getUserName())
                .build();
        return ResponseDTO.ok(syncCommandService.startSync(vo));
    }

    /**
     * 厂商 `GET /v1/sync/database/exist?url=`（`SyncController.java:1339-1361`）：
     * 打对端 `http://<url>/api/gaop/v1/sync/database/status`，10 秒超时，读 `data` 布尔值；
     * **任何异常/非 2xx ⇒ `false`**（不是错误响应）。
     */
    @GetMapping("/database/exist")
    public ResponseDTO<Boolean> existDatabaseFile(@RequestParam String url) {
        return ResponseDTO.ok(syncCommandService.databaseExistsOnPeer(url));
    }
}
