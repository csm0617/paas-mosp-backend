package com.csm.paas.core.domain.file.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对象存储 stat（HEAD object）的结果，对应厂商 {@code io.minio.StatObjectResponse} 里被用到的两个字段。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/service/FileMinioService.java:158-159}
 * （只取 {@code stat.contentType()}；长度另从 {@code g_file.size} 取）。
 *
 * <p>保留 {@code size} 是为了让端口实现能顺带校验对象是否真的存在与大小是否一致，
 * 但本仓照抄厂商语义：**下载的 Content-Length 一律用库里 {@code g_file.size}**，不用这个 size。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileObjectStatVO {

    private String contentType;
    private long size;
}
