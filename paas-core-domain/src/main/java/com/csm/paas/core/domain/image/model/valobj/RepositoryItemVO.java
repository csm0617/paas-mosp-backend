package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 镜像仓库条目（厂商 `third/response/harbor/repositories/RepositoryItem`，14 个字段）。
 *
 * <p>字段序=厂商声明序：`creationTime`→`updateTime`→`maxTagsCount`→`projectId`→`tagsCount`
 * →`name`→`description`→`id`→`pullCount`→`starCount`→`labels`→`relateSoftwareList`
 * →`lastVersion`→`lastUserName`。
 *
 * <p>K3s 实现里**只设** `id`/`name`/`tagsCount`/`lastVersion`/`updateTime`/`lastUserName`
 * /`description`/`relateSoftwareList`；`creationTime` **从不赋值**（却拿它排序，`nullsLast`）。
 * 其余字段保持 null。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepositoryItemVO {

    private String creationTime;
    private String updateTime;
    private Integer maxTagsCount;
    private Integer projectId;
    private Integer tagsCount;
    private String name;
    private String description;
    private Integer id;
    private Integer pullCount;
    private Integer starCount;
    private List<String> labels;
    private List<RelateSoftwareInfoVO> relateSoftwareList;
    private String lastVersion;
    private String lastUserName;
}
