package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库用户（厂商 `model/DatabaseUser`，6 键：id/username/password/updatedAt/createdAt/desc）。
 *
 * <p>前端 `p__MySQLSetting__components__UserInfo` 直接把它当表格行用。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseUserDTO {

    private Long id;
    private String username;
    private String password;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
}
