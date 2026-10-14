package com.csm.paas.core.domain.backup.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

/**
 * `POST /v1/backup/singleDownload` 的下载描述（跨层传递用）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../BackService.downloadSingleBackup:1228-1334` +
 * `downloadBackFile:1336-1387`。厂商直接在 service 里写 `HttpServletResponse`；
 * 本仓 domain 不依赖 servlet API，故 domain 只负责**选文件 + 解析 Range**，
 * 由 trigger 层按下面的字段写响应头与字节流（顺序见
 * `BackService.downloadBackFile`：Content-Type / Accept-Ranges / Last-Modified /
 * Expires / Content-Range / Access-Control-Expose-Headers / Content-Length / attachment 文件名）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupDownloadEntity {

    /** 要写出的文件。 */
    private File file;

    /** `Content-Disposition` 附件名（厂商用 `backup.getDatabaseFilename()` 或入参 `fileName`）。 */
    private String attachmentName;

    private long start;
    private long end;

    /** 本次要发送的字节数（`end - start + 1`）。 */
    private long fullLength;

    /** 文件总长（`Content-Range` 里的 `/total`）。 */
    private long total;
}
