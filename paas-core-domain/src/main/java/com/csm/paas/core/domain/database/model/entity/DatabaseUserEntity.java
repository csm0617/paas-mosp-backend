package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库用户（厂商 `model/DatabaseUser`，表 `g_database_user` 6 列）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseUserEntity {

    private Long id;
    private String username;
    private String password;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
}
