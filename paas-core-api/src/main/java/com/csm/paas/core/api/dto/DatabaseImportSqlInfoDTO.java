package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 导入结果（厂商 {@code controller/form/ImportSqlInfo.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/form/ImportSqlInfo.java:5-7`。
 * <p>两个键都是**库名列表**（不是文件名）：成功/失败分别收集 `dbName`
 * （厂商 `DatabaseService.importDataBase:2031-2032`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseImportSqlInfoDTO {

    private List<String> successList;
    private List<String> errorList;
}
