package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Harbor 项目（厂商 `ProjectItem`，**11 键**，与 Harbor `/api/v2.0/projects` 的元素一一对应）。
 * <p>字段名用 camelCase，序列化时按厂商的 `@JsonProperty` 转成 snake_case——
 * **字段名与顺序都是契约**（实测样本 `runtime-samples/v1_image_projectList.json`）。
 * <p>`creationTime` 是 **String**（不是时间类型）：Harbor 里可能是畸形值（实测出现过 `+58753-04-29…`），
 * 厂商排序也**按字符串比较**（`Comparator.comparing(ProjectItem::getCreationTime)`），照抄。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectItemVO {

    private String creationTime;
    private String updateTime;
    private Boolean togglable;
    private Integer currentUserRoleId;
    private Object metadata;
    private Integer deleted;
    private String ownerName;
    private Integer projectId;
    private Integer ownerId;
    private String name;
    private Integer repoCount;
}
