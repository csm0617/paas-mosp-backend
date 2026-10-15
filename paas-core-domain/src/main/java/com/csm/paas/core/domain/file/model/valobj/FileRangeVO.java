package com.csm.paas.core.domain.file.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Range 解析结果（厂商 {@code com.glriverside.gaopfdfs.response.Range}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/Range.java:9-59}
 * 与 {@code FileMinioService.java:300-322} 的 {@code getRange}。
 *
 * <p>照抄点：{@code subLong} 在子串为空时返回 **-1**（表示"未给出"），
 * {@code getRange} 据此把 {@code bytes=-N} 解释成"最后 N 字节"、把缺省/越界上界夹到 {@code size-1}。
 * 与本仓 {@code BackupFileRangeVO} 完全同构，那一条走的是本地备份文件，本类走对象存储。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileRangeVO {

    private long start;
    private long end;
    private long fullLength;
    private long total;

    /** 照抄厂商 {@code Range.subLong}：空串 → -1。 */
    public static long subLong(String value, int beginIndex, int endIndex) {
        String substring = value.substring(beginIndex, endIndex);
        return substring.length() > 0 ? Long.parseLong(substring) : -1L;
    }
}
