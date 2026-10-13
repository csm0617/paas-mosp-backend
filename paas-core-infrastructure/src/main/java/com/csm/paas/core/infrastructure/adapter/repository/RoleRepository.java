package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleUserVO;
import com.csm.paas.core.infrastructure.dao.IRoleDao;
import com.csm.paas.core.infrastructure.dao.po.RolePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    public List<RoleEntity> queryPage(int offset, int size) {
        return roleDao.selectPage(offset, size).stream().map(this::toEntity).toList();
    }

    @Override
    public long count() {
        return roleDao.selectCount();
    }

    @Override
    public List<RoleEntity> queryByUsername(String username) {
        return roleDao.selectByUsername(username).stream().map(this::toEntity).toList();
    }

    @Override
    public Optional<RoleEntity> queryByKey(String key) {
        return Optional.ofNullable(roleDao.selectByKey(key)).map(this::toEntity);
    }

    @Override
    public Optional<RoleEntity> queryById(String id) {
        return Optional.ofNullable(roleDao.selectById(id)).map(this::toEntity);
    }

    @Override
    public List<RoleEntity> queryPageByType(int type, int offset, int size) {
        return roleDao.selectPageByType(type, offset, size).stream().map(this::toEntity).toList();
    }

    @Override
    public long countByType(int type) {
        return roleDao.selectCountByType(type);
    }

    @Override
    public List<RoleEntity> queryByTypeAndNameLike(int type, String likeKeyword) {
        return roleDao.selectByTypeAndNameLike(type, likeKeyword).stream().map(this::toEntity).toList();
    }

    @Override
    public List<RoleEntity> queryByNameLike(String likeKeyword) {
        return roleDao.selectByNameLike(likeKeyword).stream().map(this::toEntity).toList();
    }

    @Override
    public List<RoleUserVO> queryUsersByRoleId(String roleId) {
        return roleDao.selectUsersByRoleId(roleId).stream()
                .map(po -> RoleUserVO.builder().userId(po.getUserId()).userName(po.getUserName()).build())
                .toList();
    }

    @Override
    public List<String> querySubRoleIdsByRoleId(String roleId) {
        return roleDao.selectSubRoleIdsByRoleId(roleId);
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
