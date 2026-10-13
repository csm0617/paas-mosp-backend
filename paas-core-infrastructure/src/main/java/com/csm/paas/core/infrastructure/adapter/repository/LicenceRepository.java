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

    /**
     * 原始机器码（厂商配置项 `machine.code`）。
     * <p>**这不是"占位符"，而是与厂商同性质的环境供给值**：厂商在本集群里的值是
     * `420DF971-09EC-BA4A-BE74-B4868EB29A53`（实测 6 次调用稳定），它来自部署时的配置/授权文件，
     * 不是能从代码推出来的常量。部署时按厂商同样方式供给即可。
     */
    @Value("${gaop.licence.machine-code:420DF971-09EC-BA4A-BE74-B4868EB29A53}")
    private String rawMachineCode;

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
    public String rawMachineCode() {
        return rawMachineCode;
    }
}
