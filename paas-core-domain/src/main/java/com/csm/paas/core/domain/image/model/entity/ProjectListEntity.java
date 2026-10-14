package com.csm.paas.core.domain.image.model.entity;

import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 项目列表（厂商 `ProjectList`：`{list,total}`；`total` = **过滤后的条数**，不是数据库总数）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectListEntity {

    private List<ProjectItemVO> list;
    private long total;
}
