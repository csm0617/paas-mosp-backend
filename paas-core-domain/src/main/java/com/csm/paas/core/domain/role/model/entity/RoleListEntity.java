package com.csm.paas.core.domain.role.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RoleListEntity {
    private List<RoleEntity> list;
    private long total;
}
