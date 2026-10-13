package com.csm.paas.core.domain.subrole.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 子角色列表分页结果（厂商 `SubRoleList`：`{list,total}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleListEntity {

    private List<SubRoleEntity> list;
    private long total;
}
