package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.image.model.valobj.TagItemVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 镜像 tag → 厂商契约键。
 *
 * <p>厂商 `third/response/harbor/tags/v2/TagItem` 的 `@JsonProperty`：
 * `pull_time` / `push_time` / `repository_id` 是 snake_case，其余（`artifactId`/`id`/`immutable`/
 * `name`/`signed`/`workLoad`）是原样 camelCase。
 *
 * <p>键顺序 = 字段声明顺序（`TagItem.java:13-37`）：
 * `artifactId` → `id` → `immutable` → `name` → `pull_time` → `push_time` → `repository_id`
 * → `signed` → `workLoad`。
 *
 * <p>`pull_time`/`push_time` 是 `java.util.Date`：交给 Spring MVC 的 Jackson 按
 * `spring.jackson.date-format=yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` + `time-zone: UTC` 输出，
 * 与厂商同一份配置（`application.yml:11-13`）。
 */
public final class ImageTagAssembler {

    private ImageTagAssembler() {
    }

    public static Map<String, Object> toItem(TagItemVO item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("artifactId", item.getArtifactId());
        map.put("id", item.getId());
        map.put("immutable", item.getImmutable());
        map.put("name", item.getName());
        map.put("pull_time", item.getPullTime());
        map.put("push_time", item.getPushTime());
        map.put("repository_id", item.getRepositoryId());
        map.put("signed", item.getSigned());
        map.put("workLoad", item.getWorkLoad());
        return map;
    }
}
