package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** `g_white_list` 行。 */
@Data
public class WhiteListPO {

    private Long id;
    private String path;
    private Long createdAt;
    private Long updatedAt;
}
