package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.SyncStatusDTO;
import com.csm.paas.core.domain.sync.service.ISyncQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `SyncController`（`/v1/sync`）——目前只实现 **`GET /status`**。
 *
 * <p>未实现：`POST /v1/sync`（真同步，依赖 SSH/rsync）、`GET /v1/sync/database/exist`（打对端节点接口）、
 * 以及 `/base_url`、`/{roleId}/users`、`/user`、`/role`、`/subRole`、`/software` 等对端拉取端点。
 */
@RestController
@RequestMapping("/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final ISyncQueryService syncQueryService;

    /**
     * 厂商 `GET /v1/sync/status`：当前同步状态（厂商直接返回 static 单例）。
     *
     * <p>未实现 `POST /v1/sync` ⇒ 本状态恒为初始值（`syncing=false`），与"没有同步在跑"时可观测结果一致。
     * 规则见 `ISyncQueryService.queryStatus`（规格 `findings/153`）。
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
}
