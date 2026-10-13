package com.csm.paas.core.domain.user.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 用户管理列表分页结果（厂商 {@code UserInfoResponse} 的领域形态）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoPageEntity {

    private List<UserInfoEntity> list;
    private long total;
}
