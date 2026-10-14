package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.image.model.valobj.RelateSoftwareInfoVO;
import com.csm.paas.core.domain.image.model.valobj.RepositoryItemVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 镜像仓库条目 → 厂商契约键（**键名与顺序都是契约**）。
 *
 * <p>厂商 `third/response/harbor/repositories/RepositoryItem` 用 `@JsonProperty` 把 camelCase 转成
 * snake_case，但**只转它标注的那些**：`relateSoftwareList`/`lastVersion`/`lastUserName` 三个键
 * 在注解里就是 camelCase（`@JsonProperty("relateSoftwareList")` 等）——照抄。
 *
 * <p>键顺序 = 厂商字段声明顺序（`RepositoryItem.java:17-59`）：
 * `creation_time` → `update_time` → `max_tags_count` → `project_id` → `tags_count` → `name`
 * → `description` → `id` → `pull_count` → `star_count` → `labels` → `relateSoftwareList`
 * → `lastVersion` → `lastUserName`。
 *
 * <p>为什么用显式 Map 而不是 DTO + Jackson：域对象是 camelCase 且域层不许引 Jackson；
 * 显式 Map 让"键名就是契约"在代码里看得见（与 {@link ImageProjectAssembler} 同一手法）。
 */
public final class ImageRepositoryAssembler {

    private ImageRepositoryAssembler() {
    }

    public static Map<String, Object> toItem(RepositoryItemVO item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("creation_time", item.getCreationTime());
        map.put("update_time", item.getUpdateTime());
        map.put("max_tags_count", item.getMaxTagsCount());
        map.put("project_id", item.getProjectId());
        map.put("tags_count", item.getTagsCount());
        map.put("name", item.getName());
        map.put("description", item.getDescription());
        map.put("id", item.getId());
        map.put("pull_count", item.getPullCount());
        map.put("star_count", item.getStarCount());
        map.put("labels", item.getLabels());
        map.put("relateSoftwareList", toRelateList(item.getRelateSoftwareList()));
        map.put("lastVersion", item.getLastVersion());
        map.put("lastUserName", item.getLastUserName());
        return map;
    }

    public static Map<String, Object> toRelate(RelateSoftwareInfoVO relate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("serviceName", relate.getServiceName());
        map.put("serviceKey", relate.getServiceKey());
        return map;
    }

    private static List<Map<String, Object>> toRelateList(List<RelateSoftwareInfoVO> list) {
        if (list == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (RelateSoftwareInfoVO relate : list) {
            result.add(toRelate(relate));
        }
        return result;
    }
}
