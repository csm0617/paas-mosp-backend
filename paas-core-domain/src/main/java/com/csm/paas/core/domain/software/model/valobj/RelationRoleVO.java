package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 软件关联的战位（厂商 `RelationRole`：`{id,name,key}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RelationRoleVO {

    private String id;
    private String name;
    private String key;
}
