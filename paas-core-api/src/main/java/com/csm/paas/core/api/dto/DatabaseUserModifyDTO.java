package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 修改数据库用户表单（厂商 `controller/response/database/DatabaseUserModify`：password/desc/updatedAt）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseUserModifyDTO {

    private String password;
    private String desc;
    private Long updatedAt;
}
