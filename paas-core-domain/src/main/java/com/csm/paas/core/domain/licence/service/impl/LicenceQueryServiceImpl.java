package com.csm.paas.core.domain.licence.service.impl;

import com.csm.paas.core.domain.licence.adapter.repository.ILicenceRepository;
import com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity;
import com.csm.paas.core.domain.licence.service.ILicenceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 授权信息。
 * <p>照抄厂商 `LicenceService.getLicenceInfo()` 的**未激活分支**：没有激活码时给试用期，
 * 首次调用会把 `g_setting['system.active.trial_period']` 落库为 now + 30 天，之后一直复用它。
 * <p>激活码校验（RSA、机器码派生）属另一域（gaop-auth），本实现只做 shape 与试用语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenceQueryServiceImpl implements ILicenceQueryService {

    static final String ACTIVE_CODE_KEY = "system.active.code";
    static final String TRIAL_PERIOD_KEY = "system.active.trial_period";
    static final long TRIAL_DAYS = 30L;

    private final ILicenceRepository licenceRepository;

    @Override
    public LicenceInfoEntity queryInfo() {
        long now = System.currentTimeMillis();
        String machineCode = machineCode();
        String activeCode = licenceRepository.queryActiveCode().orElse(null);
        if (activeCode != null) {
            // 激活码校验未实现（属 gaop-auth 域）——保守地按"未激活"处理，并记录
            log.warn("存在激活码但校验未实现，按未激活处理");
        }
        long trialPeriod = licenceRepository.querySettingValue(TRIAL_PERIOD_KEY)
                .map(Long::parseLong)
                .orElseGet(() -> {
                    long expireAt = now + TRIAL_DAYS * 24 * 3600 * 1000L;
                    licenceRepository.saveSettingValue(TRIAL_PERIOD_KEY, String.valueOf(expireAt), "试用时间");
                    return expireAt;
                });
        return LicenceInfoEntity.builder()
                .end(-1L)
                .endIso8601(null)
                .code(machineCode)
                .expired(true)
                .authStatus(false)
                .currentTime(now)
                .trialPeriod(trialPeriod)
                .build();
    }


    @Override
    public boolean checkAndActiveGis() {
        // 厂商在本环境返回 false（无 GIS 授权）；GIS 在线激活属 gaop-auth 域，未实现
        return false;
    }

    /** 厂商 `DEFAULT_GAOP = "gaop"`（`LicenceService.java:99`），以及平台 id 硬编码 `gaop`
     *  （`TenxCloudImpl.licencePlatform():539-543`）。 */
    private static final String DEFAULT_GAOP = "gaop";

    @Override
    public String machineCode() {
        // 厂商 getMachineCodeBase64()：原始机器码 + "," + "gaop" + ","（**尾部多一个逗号**）
        return Base64.getEncoder().encodeToString(
                (licenceRepository.rawMachineCode() + "," + DEFAULT_GAOP + ",").getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public String machineCodeForSoftware() {
        // 厂商 getMachineCodeBase64ByService()：原始机器码 + "," + "gaop"（**没有尾逗号**）
        return Base64.getEncoder().encodeToString(
                (licenceRepository.rawMachineCode() + "," + DEFAULT_GAOP).getBytes(StandardCharsets.US_ASCII));
    }
}
