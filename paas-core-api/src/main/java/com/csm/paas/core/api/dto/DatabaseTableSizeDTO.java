package com.csm.paas.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 库/表大小（厂商 {@code controller/response/database/DatabaseTableSizeEntity.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/database/DatabaseTableSizeEntity.java:8-19`。
 * <p>照抄点：第一层是**库**、第二层是**表**；`children` 带
 * `@JsonInclude(NON_NULL)`——**库下没有表时该键完全消失**（不是 `[]`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseTableSizeDTO {

    private String name;
    private Double size;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<DatabaseTableSizeDTO> children;
}
