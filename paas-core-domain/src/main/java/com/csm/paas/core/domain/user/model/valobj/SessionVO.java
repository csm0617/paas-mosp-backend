package com.csm.paas.core.domain.user.model.valobj;

import lombok.Builder;
import lombok.Data;

/** 会话内容：Redis 值 `<username>:<战位key>` 的结构化形态。 */
@Data
@Builder
public class SessionVO {
    private String username;
    private String roleKey;
}
