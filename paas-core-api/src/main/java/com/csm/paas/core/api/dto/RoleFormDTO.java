package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `POST /v1/role` / `PUT /v1/role/{id}` 的请求体（`RoleForm`）。
 * <p>三个列表字段的含义（与 `UserForm` 同名但语义不同，容易搞混）：
 * <ul>
 *   <li>{@code softwareList} —— **软件名**列表（厂商按 `g_software.name` 反查 id）；</li>
 *   <li>{@code userList} —— **用户名**列表（厂商按 `g_user.username` 反查 id）；</li>
 *   <li>{@code subRoleIdList} —— 子角色 **id** 列表。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleFormDTO {

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
