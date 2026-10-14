package com.csm.paas.core.domain.dynamicloading.service.impl;

import com.csm.paas.core.domain.dynamicloading.adapter.repository.IAuthInfoRepository;
import com.csm.paas.core.domain.dynamicloading.service.IDynamicLoadingCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动态加载授权 jar 写操作（规格：厂商 `DynamicLoadingService.updateDefaultAuth:64-97`）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicLoadingCommandServiceImpl implements IDynamicLoadingCommandService {

    /** 厂商硬编码：`String defaultAuth = "default_auth";`（`DynamicLoadingService.java:82`）。 */
    private static final String DEFAULT_AUTH_JAR_NAME = "default_auth";

    private final IAuthInfoRepository authInfoRepository;

    @Override
    public boolean updateDefaultAuth(String publicKey, String privateKey) {
        var row = authInfoRepository.queryLatestByJarName(DEFAULT_AUTH_JAR_NAME);
        if (row.isEmpty()) {
            // 厂商：select 为空 ⇒ 直接 return false（不写库）
            return false;
        }
        // 厂商照抄点：日志里把 privateKey 当成 publicKey 打印（`DynamicLoadingService.java:78`）——
        // 这是厂商的笔误，我们照抄这个"键名写错"的怪点，不改。
        log.info("获取到公钥的信息:{}", privateKey);
        authInfoRepository.updateDefaultAuthKeys(
                row.get().getId(), 1, publicKey, privateKey, System.currentTimeMillis());
        return true;
    }
}
