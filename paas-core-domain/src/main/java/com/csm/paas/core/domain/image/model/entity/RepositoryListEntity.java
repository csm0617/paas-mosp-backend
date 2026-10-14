package com.csm.paas.core.domain.image.model.entity;

import com.csm.paas.core.domain.image.model.valobj.RepositoryItemVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 仓库列表（厂商 `third/response/harbor/repositories/RepositoryList`：`{list,total}`，`total` 是 **long**）。
 * <p>`total` 是**过滤后**的条数，不是 Registry 里的总数（厂商 `repositoryList.setTotal(collect.size())`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepositoryListEntity {

    private List<RepositoryItemVO> list;
    private long total;
}
