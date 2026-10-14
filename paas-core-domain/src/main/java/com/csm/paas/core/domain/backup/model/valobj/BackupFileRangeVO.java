package com.csm.paas.core.domain.backup.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Range 解析结果（厂商 {@code controller/response/FileRange.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/FileRange.java:18-46`。
 * <p>照抄点：`subLong` 在子串为空时返回 **-1**（表示"未给出"），
 * `getRange` 据此把 `bytes=-N` 解释成"最后 N 字节"、把缺省的上界解释成 `size-1`
 * （厂商 `BackService.getRange:1204-1226`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupFileRangeVO {

    private long start;
    private long end;
    private long fullLength;
    private long total;

    /** 照抄厂商 `FileRange.subLong`：空串 → -1。 */
    public static long subLong(String value, int beginIndex, int endIndex) {
        String substring = value.substring(beginIndex, endIndex);
        return substring.length() > 0 ? Long.parseLong(substring) : -1L;
    }
}
