package com.csm.paas.core.domain.user.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户管理列表的一行（厂商 {@code UserInfoList} 的领域形态）。
 * <p>规则来源：厂商 {@code UserService.getUserList}（{@code UserService.java:1828-1867}）——
 * 跳过 {@code root} 用户、昵称为空串时置 null、{@code roles} 取 {@code g_role.type=1} 的名字、
 * {@code belongUsers} 取 {@code g_role.type=2} 的名字（厂商字段名错位）、{@code subRoles} 取子角色名
 * **且为空时不落值**（序列化成 null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoEntity {

    private String id;
    private String nickname;
    private String username;
    private Integer status;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    private String lastLoginRole;
    private List<String> roles;
    private List<String> subRoles;
    private List<String> belongUsers;
}
