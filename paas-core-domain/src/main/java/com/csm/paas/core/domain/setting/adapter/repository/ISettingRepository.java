package com.csm.paas.core.domain.setting.adapter.repository;

import com.csm.paas.core.domain.setting.model.entity.SettingEntity;

import java.util.List;

/** 系统设置仓储（g_setting）。 */
public interface ISettingRepository {

    /** 键不存在或不是整数时返回默认值（厂商对会话时长的默认是 30 分钟）。 */
    int queryIntValue(String key, int defaultValue);

    List<SettingEntity> queryAll();
}
