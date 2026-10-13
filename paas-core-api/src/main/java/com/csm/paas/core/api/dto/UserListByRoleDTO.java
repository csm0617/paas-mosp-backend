package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `GET /v1/user/{roleId}/users` 的响应体（`UserList`）：`{userList,total}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserListByRoleDTO {

    private List<UserDTO> userList;
    private long total;
}
