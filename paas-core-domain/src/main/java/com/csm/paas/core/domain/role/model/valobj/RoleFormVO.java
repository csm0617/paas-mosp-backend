package com.csm.paas.core.domain.role.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 新增/修改战位的表单（厂商 `RoleForm`）。
 * <p>三个列表字段：`softwareList`=**软件名**、`userList`=**用户名**、`subRoleIdList`=子角色 **id**
 * （厂商在 service 里逐个反查 id，找不到就跳过）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleFormVO {

    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private String desc;
    private Integer type;
    private List<String> softwareList;
    private List<String> userList;
    private List<String> subRoleIdList;
}
