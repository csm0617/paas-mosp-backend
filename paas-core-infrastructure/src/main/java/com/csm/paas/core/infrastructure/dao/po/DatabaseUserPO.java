package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/**
 * `g_database_user` 行（厂商 `model/DatabaseUser`，建表脚本
 * `recovered/gaop-be/src/main/resources/db/migration/V0.0.8__add-database-user-table.sql`）。
 */
@Data
public class DatabaseUserPO {

    /** `id bigint not null auto_increment`。 */
    private Long id;
    /** `username varchar(36)`。 */
    private String username;
    /** `password varchar(100)`（厂商库里存的是明文，照抄）。 */
    private String password;
    /** 列名带反引号（MySQL 保留字）。 */
    private String desc;
    private Long updatedAt;
    private Long createdAt;
}
