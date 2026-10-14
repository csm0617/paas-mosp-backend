package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 导入结果（厂商 {@code controller/form/ImportSqlInfo}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.importDataBase:2031-2032`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseImportSqlInfoEntity {

    private List<String> successList;
    private List<String> errorList;
}
