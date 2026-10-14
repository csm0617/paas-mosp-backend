package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `RoleUsers`（`RoleUsers extends Role` + `List<User> list`）的响应体：
 * `POST /v1/role/{roleId}/users`、`DELETE /v1/role/{roleId}/{userId}` 用它。
 *
 * <p>**不要与 {@link RoleItemDTO} 混用**：`RoleItemDTO.userList` 是 `{userId,userName}` 的
 * `RoleUser`（`/v1/role/list` 的键名），这里的 `list` 是**完整 `User`**（含 `password`），
 * 且四个集合键（`onLineUserList`/`softwareList`/`userList`/`subRoleList`）**恒为 null**
 * （厂商 `BeanUtils.copyProperties` 只搬标量）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleUsersDTO {

    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    private Integer type;
    /** 恒为 null（厂商这条路径不填）。 */
    private List<String> onLineUserList;
    /** 恒为 null。 */
    private List<RoleSoftwareDTO> softwareList;
    /** 恒为 null——注意与 `list` 的区别。 */
    private List<RoleUserDTO> userList;
    /** 恒为 null。 */
    private List<SubRoleItemDTO> subRoleList;
    private Boolean adminOrDev;
    private Boolean admin;
    private Boolean dev;
    /** 该战位下的**完整用户**（含 `password` 等 14 列，不过滤 root、不排序）。 */
    private List<UserDTO> list;
}
