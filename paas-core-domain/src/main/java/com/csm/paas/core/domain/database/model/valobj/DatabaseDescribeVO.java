package com.csm.paas.core.domain.database.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `PUT /v1/database/{dataBaseName}` 的入参（厂商 {@code controller/response/database/DatabaseDescribe}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.updateDatabaseByName:592-602`：
 * 库不存在时**只有** `service`/`desc` 被写进 `g_database`（`userName` 被丢弃）；
 * 库存在时先 `updateDatabaseByName(desc, service)` 再 `updateDatabaseByLastUser(userName)`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseDescribeVO {

    private String service;
    private String desc;
    private String userName;
}
