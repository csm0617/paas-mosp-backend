package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 库 ↔ 用户关联（厂商 {@code controller/response/DatabaseUserRelationInfo}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.getRelationDataBaseList:2378-2396`
 * （遍历业务库，`mysql.db` 里 `Db=<库> and Host='%'` 的 user 列表非空才收集）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseUserRelationInfoEntity {

    private String databaseName;
    private List<String> databaseUserList;
}
