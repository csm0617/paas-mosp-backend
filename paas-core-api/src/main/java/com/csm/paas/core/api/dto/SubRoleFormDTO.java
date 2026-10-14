package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子角色新增/修改表单（厂商 `SubRoleUserForm` / `SubRoleUpdateReq` 的并集）。
 * <p>新增：`POST /v1/sub_role`（`id` 忽略，服务端生成）；修改：`PUT /v1/sub_role/update`（`id` 必填）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleFormDTO {

    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private String desc;
    private List<String> userList;
}
