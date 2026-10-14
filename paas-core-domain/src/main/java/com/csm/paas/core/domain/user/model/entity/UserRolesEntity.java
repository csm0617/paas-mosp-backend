package com.csm.paas.core.domain.user.model.entity;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * `GET /v1/user/list` 的条目（厂商 `UserRoles`，**16 键**）。
 *
 * <p>与 `/v1/user/info` 的条目（11 键）差异见 `findings/98`：
 * 去掉 `belongUsers`，多 `email/birth/phone/avatar/uuid` 与布尔 `admin`。
 * <p><b>三处照抄点</b>：`nickname` 恒 null（厂商没 set）；`roles` 是**完整 Role 对象**；
 * `subRoles` 空时是 **`[]`** 不是 null。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRolesEntity {

    private String id;
    private String username;
    private Integer status;
    private String email;
    private LocalDate birth;
    private String phone;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    private String avatar;
    private String lastLoginRole;
    private Integer uuid;
    private List<RoleEntity> roles;
    private List<SubRoleEntity> subRoles;
    private boolean admin;
}
