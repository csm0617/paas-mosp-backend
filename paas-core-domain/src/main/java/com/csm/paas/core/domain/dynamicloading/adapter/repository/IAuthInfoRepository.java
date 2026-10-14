package com.csm.paas.core.domain.dynamicloading.adapter.repository;

import com.csm.paas.core.domain.dynamicloading.model.entity.AuthInfoEntity;

import java.util.Optional;

/**
 * 授权 jar 记录仓储（厂商 `AuthInfoMapper`，表 `g_auth`）。
 */
public interface IAuthInfoRepository {

    /**
     * 厂商 `DynamicLoadingService.updateDefaultAuth:86-88`：
     * `where jar_name = 'default_auth' order by create_at DESC`，取**第 0 条**。
     * <p>查不到 ⇒ `Optional.empty()`（厂商此时直接返回 `false`）。
     */
    Optional<AuthInfoEntity> queryLatestByJarName(String jarName);

    /**
     * 厂商同方法 `:89-95`：`updateByPrimaryKeySelective` 只改
     * `used` / `public_key` / `private_key` / `update_at` 四列。
     */
    void updateDefaultAuthKeys(Integer id, Integer used, String publicKey, String privateKey, Long updateAt);
}
