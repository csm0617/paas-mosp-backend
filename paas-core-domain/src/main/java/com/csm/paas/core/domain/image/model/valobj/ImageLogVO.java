package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 镜像上传日志（厂商 `model/ImageLog`，表 `g_image_log`，9 列）。
 *
 * <p>字段名与列名一一对应（`fileName`→`file_name`、`createdAt`→`created_at`、…）。
 * <p>`status`：0 失败、1 成功、2 处理中（厂商 DDL 注释）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageLogVO {

    private Long id;
    private String fileName;
    private String name;
    private String version;
    private Integer status;
    private Long createdAt;
    private Long updatedAt;
    private String username;
    private String log;
}
