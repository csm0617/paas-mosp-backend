package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 白名单条目（厂商 `model/WhiteList`）：`{id,path,createdAt,updatedAt}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WhiteListDTO {

    private Long id;
    private String path;
    private Long createdAt;
    private Long updatedAt;
}
