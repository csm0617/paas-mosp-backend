package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 厂商 `GET /v1/role`、`/v1/role/list`、`/v1/role/switchRole`、`/v1/role/{id}` → `data`（或 `data.list[]`）的 16 键形状。
 * <p>四个端点**同一个模型、不同填充**，必须区分（实测样本）：
 * <ul>
 *   <li>`/v1/role`：`onLineUserList`/`userList`/`subRoleList` 全为 **null**；</li>
 *   <li>`/v1/role/list`：`userList` **有值**，另两个 null；</li>
 *   <li>`/v1/role/switchRole`：三者**都有值**（`onLineUserList` 来自 Redis `login-list*` 扫描）；</li>
 *   <li>`/v1/role/{id}`：四者**全为 null**，但 `nameFr`/`nameUs` 会填成 `name`。</li>
 * </ul>
 */
@Data
@Builder
public class RoleItemDTO {
    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    private Integer type;
    /** 该战位当前在线的用户名（Redis `login-list:<user>:<roleKey>` 归组）；无人在线是 `[]`。 */
    private List<String> onLineUserList;
    private List<RoleSoftwareDTO> softwareList;
    /** 关联用户 `{userId,userName}`。 */
    private List<RoleUserDTO> userList;
    /** 关联子角色（与 `/v1/sub_role/switchRole` 的项同形状）。 */
    private List<SubRoleItemDTO> subRoleList;
    /** 由战位 key 推导（admin/dev）——照抄厂商语义。 */
    private Boolean adminOrDev;
    private Boolean admin;
    private Boolean dev;
}
