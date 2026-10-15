package com.csm.paas.core.domain.file.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件下载的"跨层描述"：domain 只算 metainfo 与 Range，字节流由 trigger 层写响应。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/service/FileMinioService.java:141-180}
 * （MinIO 分支，直接写 {@code HttpServletResponse}）+ {@code utils/MinioClientUtil.java:81-127}。
 *
 * <p>为什么要拆：本仓 domain 不依赖 servlet API（对照 {@code BackupDownloadEntity} 的做法），
 * 所以 domain 返回本对象，trigger 负责 setStatus + 写头 + 调对象存储端口取字节。
 *
 * <p>照抄点（字段全部来自厂商写响应头时的取值，顺序见 FileMinioService:158-175）：
 * <ul>
 *   <li>{@code contentType} 来自对象存储的 stat（HEAD object），**不是**库里存的；</li>
 *   <li>{@code md5} 写进 {@code ETag} 头，来自 {@code g_file.md5}；</li>
 *   <li>{@code lastModified} = {@code updatedAt != null ? updatedAt : createdAt}（毫秒）；</li>
 *   <li>{@code start}/{@code end}/{@code total} 写 {@code Content-Range}，
 *       {@code fullLength = end - start + 1} 写 {@code Content-Length}；</li>
 *   <li>{@code partial} 分支由 {@code rangeHeader} 是否非 null 决定：非 null ⇒ 厂商
 *       {@code handleRangeRequest}（HTTP 206），null ⇒ {@code handleFullDownload}（HTTP 200）。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileDownloadEntity {

    /** 对象存储里的 objectName（{@code g_file.path}），trigger 用它取字节流。 */
    private String objectName;

    /** {@code Content-Disposition} 附件名（{@code g_file.name}，UTF-8 + URL 编码）。 */
    private String attachmentName;

    /** 来自 stat object 的 Content-Type（厂商拿不到时会走 MinIO 异常分支）。 */
    private String contentType;

    /** 写进 {@code ETag} 头（{@code g_file.md5}）。 */
    private String md5;

    /** {@code Last-Modified}，毫秒。 */
    private long lastModified;

    private long start;
    private long end;

    /** 本次要发送的字节数（{@code end - start + 1}）。 */
    private long fullLength;

    /** 对象总长（{@code Content-Range} 里的 {@code /total}）。 */
    private long total;

    /**
     * 客户端 {@code Range} 头**补上 {@code bytes=} 前缀后**的原文；没传 Range 时为 null。
     *
     * <p>照抄点：厂商先做 {@code range = "bytes=" + range}（{@code FileMinioService.java:152-154}），
     * 再用 {@code range != null && range.startsWith("bytes=")} 决定走 206 分支；
     * 206 分支内部**又把这个字符串重新解析一遍**（{@code parseRangeHeader}），
     * 且传入的"文件大小"是 {@code fullLength} 而不是对象总长——两处怪点都在 trigger 层照抄。
     */
    private String rangeHeader;
}
