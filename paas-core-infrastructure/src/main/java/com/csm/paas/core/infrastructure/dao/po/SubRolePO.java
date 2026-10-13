package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** `g_sub_role` 行。 */
@Data
public class SubRolePO {

    private String id;
    private String key;
    private String name;
    private String desc;
    private Long updatedAt;
    private Long createdAt;
}
