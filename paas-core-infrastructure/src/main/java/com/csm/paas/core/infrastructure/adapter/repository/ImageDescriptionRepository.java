package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.image.adapter.repository.IImageDescriptionRepository;
import com.csm.paas.core.infrastructure.dao.IImageDescriptionDao;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 镜像描述仓储（`g_setting` 的 `image.desc` 键）。
 *
 * <p>JSON 的读写在这里（域层不许 import Jackson）：
 * 厂商用 Gson 把 `HashMap<String,String>` 序列化成 `{"repoId":"desc",...}`，这里用 Jackson 等价实现。
 * 写入语义照抄厂商 `K3sImageServiceImpl.updateImageDesc:460-485`：
 * 行不存在 ⇒ 只写 `val`/`created_at`；已存在 ⇒ 只更新 `val`/`created_at`（**不是 `updated_at`**）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageDescriptionRepository implements IImageDescriptionRepository {

    private final IImageDescriptionDao imageDescriptionDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Optional<Map<String, String>> queryMap() {
        String val = imageDescriptionDao.selectImageDesc();
        if (val == null) {
            return Optional.empty();
        }
        try {
            Map<String, String> map = objectMapper.readValue(val, new TypeReference<HashMap<String, String>>() {
            });
            return Optional.ofNullable(map);
        } catch (Exception e) {
            // 厂商 `Gson.fromJson` 同样会抛（JsonSyntaxException）⇒ 500
            throw new IllegalStateException("解析 image.desc 失败：" + val, e);
        }
    }

    @Override
    public void saveMap(Map<String, String> map, long now) {
        String json;
        try {
            json = objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 image.desc 失败", e);
        }
        if (imageDescriptionDao.countImageDesc() == 0) {
            imageDescriptionDao.insertImageDesc(json, now);
        } else {
            imageDescriptionDao.updateImageDesc(json, now);
        }
    }
}
