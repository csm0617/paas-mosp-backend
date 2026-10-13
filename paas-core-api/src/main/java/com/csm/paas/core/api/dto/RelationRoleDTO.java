package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `RelationRole`（软件关联的战位）：`{id,name,key}`。
 * <p>注意字段顺序是 **id, name, key**（厂商 `SoftwareMapper.getRelationRole` 的 select 顺序）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RelationRoleDTO {

    private String id;
    private String name;
    private String key;
}
