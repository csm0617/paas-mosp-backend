package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 数据库用户列表（厂商 `controller/response/database/DatabaseUserList`：`databaseUsers` + `total`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseUserListDTO {

    private List<DatabaseUserDTO> databaseUsers;
    private Long total;
}
