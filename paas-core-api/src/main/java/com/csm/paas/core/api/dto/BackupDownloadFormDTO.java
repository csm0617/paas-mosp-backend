package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/backup/singleDownload` 的请求体（厂商 {@code controller/form/DownloadBackup.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/form/DownloadBackup.java:6-10`。
 * <p>照抄点：`dataKey` 是必填（厂商**不校验**，为 null 时 `dataKey.equals(...)` 会 NPE → 500/-1 信封）；
 * `fileName` 只在 `dataKey=="image"` 时被用到。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupDownloadFormDTO {

    private String dataKey;
    private String fileName;
}
