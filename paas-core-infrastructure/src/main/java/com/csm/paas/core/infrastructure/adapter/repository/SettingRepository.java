package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.setting.model.entity.SettingEntity;
import com.csm.paas.core.infrastructure.dao.ISettingDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    @Override
    public List<SettingEntity> queryAll() {
        return settingDao.selectAll().stream()
                .map(po -> SettingEntity.builder()
                        .key(po.getKey()).val(po.getVal()).desc(po.getDesc())
                        .updatedAt(po.getUpdatedAt()).createdAt(po.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public Optional<String> queryValue(String key) {
        return Optional.ofNullable(settingDao.selectValByKey(key));
    }

    @Override
    public void upsertValue(String key, String val, String desc) {
        long now = System.currentTimeMillis();
        if (settingDao.countByKey(key) == 0) {
            settingDao.insertSetting(key, val, desc, now);
        } else {
            settingDao.updateSetting(key, val, desc, now);
        }
    }

    @Override
    public void deleteValue(String key) {
        settingDao.deleteSetting(key);
    }

    @Override
    public void saveValueIfAbsent(String key, String val, String desc) {
        if (settingDao.countByKey(key) == 0) {
            settingDao.insertSetting(key, val, desc, System.currentTimeMillis());
        }
    }
}
