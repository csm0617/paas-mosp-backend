package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `GET /v1/database` 的返回（厂商 {@code controller/response/database/DatabaseList}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.getUserTableCount:612-616`。
 * <p>键名照抄：**`databaseItemList`**（不是 `list`），`total` 是 `Long`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseListEntity {

    private List<DatabaseItemEntity> databaseItemList;
    private Long total;
}
