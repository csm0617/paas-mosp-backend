package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 白名单列表响应（厂商 `WhiteListInfo`）：`{list,total}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WhiteListInfoDTO {

    private List<WhiteListDTO> list;
    private long total;
}
