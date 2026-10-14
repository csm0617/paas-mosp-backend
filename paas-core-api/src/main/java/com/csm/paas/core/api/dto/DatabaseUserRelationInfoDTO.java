package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 库与用户的关联（厂商 {@code controller/response/DatabaseUserRelationInfo.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/DatabaseUserRelationInfo.java:6-8`。
 * <p>用于 `POST /v1/database/relationDataBase` 的 JSON 导出与导入包的 `databaseUserRelation.json`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseUserRelationInfoDTO {

    private String databaseName;
    private List<String> databaseUserList;
}
