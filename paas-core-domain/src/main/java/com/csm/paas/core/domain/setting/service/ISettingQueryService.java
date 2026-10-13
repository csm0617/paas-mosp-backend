package com.csm.paas.core.domain.setting.service;

import com.csm.paas.core.domain.setting.model.entity.SettingEntity;

import java.util.List;

/** 设置查询服务（厂商 GET /settings）。 */
public interface ISettingQueryService {

    List<SettingEntity> queryAll();
}
