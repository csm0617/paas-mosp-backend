package com.csm.paas.core.domain.image.adapter.repository;

import java.util.Map;
import java.util.Optional;

/**
 * 镜像描述仓储：表 `g_setting` 的键 `image.desc`（厂商 `K3sImageServiceImpl.updateImageDesc:460-485`
 * 与 `getRepositories:300-314`）。
 *
 * <p><b>值是一段 JSON 对象</b>：`{"<repoId>": "<description>", ...}`（厂商用 Gson 序列化 `HashMap`）。
 * JSON 的读写收在基础设施层（域层不许 import Jackson）。
 *
 * <p>厂商的写入语义与 paas-core 既有的 `SettingRepository.upsert*` **不同**，必须单列：
 * <ul>
 *   <li>键不存在 ⇒ `insertSelective`：只写 `key`/`val`/`created_at`（`desc` 与 `updated_at` 留 NULL）；</li>
 *   <li>键已存在 ⇒ `updateByPrimaryKeySelective`：只写 `val` 与 **`created_at`**（厂商写的是
 *       `setCreatedAt`，**不是 `updated_at`**——怪点，照抄）。</li>
 * </ul>
 */
public interface IImageDescriptionRepository {

    /**
     * 读出 `image.desc` 并解析成 `repoId -> description`。
     * <p>键不存在 ⇒ **空 Optional**（厂商此时 `imageDescMap` 保持 null）；存在但 JSON 解析失败 ⇒ 抛异常
     * （厂商 `Gson.fromJson` 会抛，最终 500）。
     */
    Optional<Map<String, String>> queryMap();

    /** 写回整个 map（厂商按"行是否存在"决定 insert / update，语义见类注释）。 */
    void saveMap(Map<String, String> map, long now);
}
