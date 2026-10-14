package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 库/表大小树（厂商 {@code controller/response/database/DatabaseTableSizeEntity}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.getBusinessDataBaseTableSize:2438-2462`。
 * <p>照抄点：库节点的 `size` 是**该库所有表 size 之和**再 `setScale(2, HALF_UP)`；
 * 表节点 `children` 为 null（序列化时 `@JsonInclude(NON_NULL)` 把它抹掉）；
 * 最后按**库 size 降序**排序（`sorted` 的稳定性保证同 size 的相对顺序来自 `groupingBy` 的 HashMap 迭代序，
 * 即**不确定**——照抄，不额外排序）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseTableSizeEntity {

    private String name;
    private Double size;
    private List<DatabaseTableSizeEntity> children;
}
