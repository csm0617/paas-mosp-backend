package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.user.adapter.repository.ISettingRepository;
import com.csm.paas.core.infrastructure.dao.ISettingDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SettingRepository implements ISettingRepository {

    private final ISettingDao settingDao;

    @Override
    public int queryIntValue(String key, int defaultValue) {
        String val = settingDao.selectValByKey(key);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("设置 {} 不是整数: {}，回落默认值 {}", key, val, defaultValue);
            return defaultValue;
        }
    }
}
