package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/**
 * `g_image_log` 行（9 列）。
 * <p>DDL 见厂商 `recovered/gaop-be/src/main/resources/db/migration/V0.4.1__add-image-log-table.sql`
 * 与 `V0.5.4__update_image_log.sql`（`username` 是后加的列）。
 */
@Data
public class ImageLogPO {

    private Long id;
    private String fileName;
    private String name;
    private String version;
    /** 0 失败、1 成功、2 处理中。 */
    private Integer status;
    private Long createdAt;
    private Long updatedAt;
    private String username;
    private String log;
}
