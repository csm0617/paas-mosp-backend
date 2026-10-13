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
 * 用户详情（厂商 `UserDetailInfo`，`GET /v1/user/detailByUserId` 的 data，**17 个键**）。
 * <p>实测样本 `docs/findings/runtime-samples/v1_user_detailByUserId.json`。
 * <p>三个集合的语义：`roles` = `g_role.type=1`（战位，带软件列表与伪门户）、
 * `belongRole` = `g_role.type=2`（角色，同样填充）、`subRoles` = 用户关联的子角色（**不填** userList/roleList）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailEntity {

    private String id;
    private String username;
    private String nickname;
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
    /** 该用户的战位列表里是否有 `admin`（厂商 `UserService.hasAdminPermission`，`:851-861`）。 */
    private Boolean admin;
    private List<RoleEntity> belongRole;
}
