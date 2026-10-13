package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 {@code GET /v1/user/info} 列表项（{@code UserInfoList}）。
 * <p>字段与声明顺序照抄厂商：{@code recovered/gaop-be/.../controller/response/UserInfoList.java}；
 * 实测样本 {@code docs/findings/runtime-samples/v1_user_info.json}。
 * <p><b>{@code belongUsers} 装的是 {@code g_role.type=2}（角色）的名字，不是用户</b>——厂商字段名错位，
 * 见 {@code docs/findings/23-k1-live-schema-and-unique-constraints.md}；契约比对必须按错位复刻。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoItemDTO {

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
