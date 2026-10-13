package com.csm.paas.core.domain.mode.model.entity;

import lombok.Builder;
import lombok.Data;

/** 作战模式（表 g_mode；`used=1` 是当前模式）。 */
@Data
@Builder
public class ModeEntity {
    private Long id;
    private Integer type;
    private String mode;
    private String modeName;
    private Integer used;
    private Long createTime;
}
