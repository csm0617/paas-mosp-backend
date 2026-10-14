package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `SyncStatus`（`GET /v1/sync/status`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncStatusDTO {

    private boolean syncing;
    private boolean success;
    private String currentSyncName;
    private String currentSyncType;
    private int current;
    private int total;
    private String errorStatus;
}
