package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据库列表（厂商 {@code controller/response/database/DatabaseList.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/database/DatabaseList.java:5-7`。
 * <p>照抄点：元素键名是 **`databaseItemList`**（不是 `list`），`total` 是包装类型 `Long`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseListDTO {

    private List<DatabaseItemDTO> databaseItemList;
    private Long total;
}
