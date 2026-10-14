package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.dynamicloading.adapter.repository.IAuthInfoRepository;
import com.csm.paas.core.domain.dynamicloading.model.entity.AuthInfoEntity;
import com.csm.paas.core.infrastructure.dao.IAuthInfoDao;
import com.csm.paas.core.infrastructure.dao.po.AuthInfoPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 授权 jar 记录仓储实现（表 `g_auth`）。
 */
@Repository
@RequiredArgsConstructor
public class AuthInfoRepository implements IAuthInfoRepository {

    private final IAuthInfoDao authInfoDao;

    @Override
    public Optional<AuthInfoEntity> queryLatestByJarName(String jarName) {
        return Optional.ofNullable(authInfoDao.selectLatestByJarName(jarName)).map(this::toEntity);
    }

    @Override
    public void updateDefaultAuthKeys(Integer id, Integer used, String publicKey, String privateKey, Long updateAt) {
        authInfoDao.updateDefaultAuthKeys(id, used, publicKey, privateKey, updateAt);
    }

    private AuthInfoEntity toEntity(AuthInfoPO po) {
        return AuthInfoEntity.builder()
                .id(po.getId()).jarName(po.getJarName()).storeJarName(po.getStoreJarName())
                .className(po.getClassName()).createAt(po.getCreateAt()).updateAt(po.getUpdateAt())
                .used(po.getUsed()).publicKey(po.getPublicKey()).privateKey(po.getPrivateKey())
                .build();
    }
}
