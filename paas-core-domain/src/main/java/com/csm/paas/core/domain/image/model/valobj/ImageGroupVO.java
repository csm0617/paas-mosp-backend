package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 镜像分组（厂商 `ImageGroup`，表 **`g_group`**：`id` / `create_date` / `name` —— **只有 3 列**）。
 * <p>`createDate` 是**毫秒**时间戳，但厂商读出来时按**秒**解释（`Instant.ofEpochSecond`）⇒ 年份会爆到 58753。
 * 这是厂商缺陷，照抄。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageGroupVO {

    private Integer id;
    private Long createDate;
    private String name;
}
