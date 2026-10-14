package com.csm.paas.core.domain.whitelist.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 白名单列表响应（厂商 `WhiteListInfo`：`{list,total}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WhiteListInfoEntity {

    private List<WhiteListEntity> list;
    private long total;
}
