package com.csm.paas.core.domain.user.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** `GET /v1/user/list` 的分页结果（厂商 `UserRoleList{list,total}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRolesPageEntity {

    private List<UserRolesEntity> list;
    private long total;
}
