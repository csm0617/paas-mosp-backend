package com.csm.paas.core.domain.role.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前在线会话的（用户名, 战位 key）对——由 Redis `login-list:<user>:<roleKey>` 键推导。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OnlineUserVO {

    private String username;
    private String roleKey;
}
