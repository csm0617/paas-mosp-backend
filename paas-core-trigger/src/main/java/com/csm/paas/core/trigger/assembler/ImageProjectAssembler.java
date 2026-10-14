package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Harbor 项目 → 厂商契约键（**snake_case**）。
 *
 * <p><b>为什么不用 DTO + Jackson 自动序列化</b>：域对象是 camelCase（`creationTime`），
 * 而厂商契约是 snake_case（`creation_time`，见 `ProjectItem` 上的 `@JsonProperty`）；
 * 域模块与 api 模块都没有 Jackson 注解依赖。用显式 Map 固定键名与顺序，
 * 既避免引依赖，也让"键名就是契约"这件事在代码里看得见。
 * <p>键的顺序照抄实测样本 `runtime-samples/v1_image_projectList.json`。
 */
public final class ImageProjectAssembler {

    private ImageProjectAssembler() {
    }

    public static Map<String, Object> toItem(ProjectItemVO p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("creation_time", p.getCreationTime());
        m.put("update_time", p.getUpdateTime());
        m.put("togglable", p.getTogglable());
        m.put("current_user_role_id", p.getCurrentUserRoleId());
        m.put("metadata", p.getMetadata());
        m.put("deleted", p.getDeleted());
        m.put("owner_name", p.getOwnerName());
        m.put("project_id", p.getProjectId());
        m.put("owner_id", p.getOwnerId());
        m.put("name", p.getName());
        m.put("repo_count", p.getRepoCount());
        return m;
    }
}
