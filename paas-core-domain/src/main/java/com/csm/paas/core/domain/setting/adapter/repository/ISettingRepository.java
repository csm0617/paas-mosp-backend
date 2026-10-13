package com.csm.paas.core.domain.setting.adapter.repository;

import com.csm.paas.core.domain.setting.model.entity.SettingEntity;

import java.util.List;
import java.util.Optional;

/** 系统设置仓储（g_setting）。 */
public interface ISettingRepository {

    /** 键不存在或不是整数时返回默认值（厂商对会话时长的默认是 30 分钟）。 */
    int queryIntValue(String key, int defaultValue);

    List<SettingEntity> queryAll();

    Optional<String> queryValue(String key);

    /** 不存在则插入（厂商会在首次调用时落库试用期）。 */
    void saveValueIfAbsent(String key, String val, String desc);

    /** 有则更新、无则插入（厂商 `saveActiveCode` 对 `system.active.code` 就是 upsert 语义）。 */
    void upsertValue(String key, String val, String desc);

    /** 删除该键（厂商 `clearActiveCode` 对 `system.active.code` 是删行）。 */
    void deleteValue(String key);
}
