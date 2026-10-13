package com.csm.paas.core.domain.setting.service.impl;

import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.setting.model.entity.SettingEntity;
import com.csm.paas.core.domain.setting.service.ISettingQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingQueryServiceImpl implements ISettingQueryService {

    private final ISettingRepository settingRepository;

    @Override
    public List<SettingEntity> queryAll() {
        List<SettingEntity> all = settingRepository.queryAll();
        log.info("查询系统设置 共 {} 条", all.size());
        return all;
    }

    @Override
    public boolean queryBooleanValue(String key) {
        return settingRepository.queryValue(key).map("true"::equals).orElse(false);
    }
}
