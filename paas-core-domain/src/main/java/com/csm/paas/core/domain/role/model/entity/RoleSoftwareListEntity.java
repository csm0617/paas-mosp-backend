package com.csm.paas.core.domain.role.model.entity;

import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 战位下的软件列表（厂商 `RoleSoftwareList`：`{list,total}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleSoftwareListEntity {

    private List<RoleSoftwareEntity> list;
    private long total;
}
