package com.csm.paas.core.domain.whitelist.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 白名单放行路径（厂商 `model/WhiteList.java`，表 `g_white_list`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WhiteListEntity {

    private Long id;
    private String path;
    private Long createdAt;
    private Long updatedAt;
}
