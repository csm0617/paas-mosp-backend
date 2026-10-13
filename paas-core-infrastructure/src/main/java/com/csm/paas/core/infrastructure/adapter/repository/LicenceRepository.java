package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.licence.adapter.repository.ILicenceRepository;
import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 授权仓储：激活码/试用期走设置仓储（`g_setting`），机器码由配置提供。 */
@Repository
@RequiredArgsConstructor
public class LicenceRepository implements ILicenceRepository {

    private final ISettingRepository settingRepository;

    /** 厂商的机器码由本机文件+配置推导；重写版先用配置项（默认占位），后续按需实现算法。 */
    @Value("${gaop.licence.machine-code:MSOP-REWRITE-PLACEHOLDER}")
    private String machineCode;

    @Override
    public Optional<String> queryActiveCode() {
        return settingRepository.queryValue("system.active.code").filter(v -> !v.isBlank());
    }

    @Override
    public Optional<String> querySettingValue(String key) {
        return settingRepository.queryValue(key);
    }

    @Override
    public void saveSettingValue(String key, String val, String desc) {
        settingRepository.saveValueIfAbsent(key, val, desc);
    }

    @Override
    public String machineCode() {
        return machineCode;
    }
}
