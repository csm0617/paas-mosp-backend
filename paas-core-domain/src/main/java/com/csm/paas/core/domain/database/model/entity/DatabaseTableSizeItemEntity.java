package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单张表的大小（厂商 {@code controller/response/database/DatabaseTableSizeItem}）。
 *
 * <p>厂商出处 SQL（`MysqlMapper.getDbTableSize`）：
 * <pre>
 * SELECT table_schema AS 'databaseName', table_name AS 'tableName',
 *        ROUND((data_length + index_length) / 1024 / 1024, 2) AS 'size'
 * FROM information_schema.tables
 * WHERE table_type = 'BASE TABLE'
 *   AND table_schema NOT IN ('information_schema','mysql','performance_schema','sys')
 * ORDER BY databaseName asc, size desc
 * </pre>
 * <p>照抄点：单位 MB 且**已在 SQL 里 ROUND 到 2 位**（Java 侧还会再 `setScale(2, HALF_UP)`）；
 * 排序是 `库名升序, 大小降序`——但厂商 `getBusinessDataBaseTableSize` 之后又对**库层**重排成
 * 大小降序（`Comparator.comparing(size).reversed()`），所以最终库顺序与 SQL 无关。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseTableSizeItemEntity {

    private String databaseName;
    private String tableName;
    private BigDecimal size;
}
