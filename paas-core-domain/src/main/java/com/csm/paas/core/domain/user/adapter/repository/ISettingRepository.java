package com.csm.paas.core.domain.user.adapter.repository;

/** 系统设置读取（g_setting），当前只用到会话时长。 */
public interface ISettingRepository {

    /** 键不存在时返回默认值（厂商默认 30 分钟）。 */
    int queryIntValue(String key, int defaultValue);
}
