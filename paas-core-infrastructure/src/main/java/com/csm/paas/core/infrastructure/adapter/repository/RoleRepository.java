package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.infrastructure.dao.IRoleDao;
import com.csm.paas.core.infrastructure.dao.po.RolePO;
import com.csm.paas.core.infrastructure.dao.po.RoleSoftwarePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Repository 实现：只调 dao，不含业务判断。 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RoleRepository implements IRoleRepository {

    private final IRoleDao roleDao;

    @Override
    public List<RoleEntity> queryOrderByCreatedAtDesc() {
        return roleDao.selectOrderByCreatedAtDesc().stream().map(this::toEntity).toList();
    }

    @Override
    public long count() {
        return roleDao.selectCount();
    }

    @Override
    public List<RoleSoftwareEntity> querySoftwareByRoleId(String roleId) {
        return roleDao.selectSoftwareByRoleId(roleId).stream()
                .map(po -> RoleSoftwareEntity.builder()
                        .id(po.getId())
                        .roleId(po.getRoleId())
                        .appId(po.getAppId())
                        .appName(po.getAppName())
                        .url(po.getUrl())
                        .updatedAt(po.getUpdatedAt())
                        .createdAt(po.getCreatedAt())
                        .build())
                .toList();
    }

    private RoleEntity toEntity(RolePO po) {
        return RoleEntity.builder()
                .id(po.getId())
                .key(po.getKey())
                .name(po.getName())
                .desc(po.getDesc())
                .type(po.getType())
                .updatedAt(po.getUpdatedAt())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
