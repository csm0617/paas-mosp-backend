package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleUserVO;
import com.csm.paas.core.infrastructure.dao.IRoleDao;
import com.csm.paas.core.infrastructure.dao.ISubRoleDao;
import com.csm.paas.core.infrastructure.dao.IUserDao;
import com.csm.paas.core.infrastructure.dao.po.RolePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Repository 实现：只调 dao，不含业务判断。 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RoleRepository implements IRoleRepository {

    private final IRoleDao roleDao;
    private final IUserDao userDao;
    private final ISubRoleDao subRoleDao;

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
    public List<String> filterExistingIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return roleDao.selectExistingIds(ids);
    }

    @Override
    public List<RoleEntity> queryByUsernameAndType(String username, int type) {
        return roleDao.selectByUsernameAndType(username, type).stream().map(this::toEntity).toList();
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
    public List<RoleSoftwareEntity> querySoftwarePage(String roleId, int offset, int size) {
        return roleDao.selectSoftwarePage(roleId, offset, size).stream().map(RoleRepository::toSoftwareEntity).toList();
    }

    @Override
    public long countSoftwareByRoleId(String roleId) {
        return roleDao.countSoftwareByRoleId(roleId);
    }

    private static RoleSoftwareEntity toSoftwareEntity(com.csm.paas.core.infrastructure.dao.po.RoleSoftwarePO po) {
        return RoleSoftwareEntity.builder()
                .id(po.getId()).roleId(po.getRoleId()).appId(po.getAppId()).appName(po.getAppName())
                .url(po.getUrl()).updatedAt(po.getUpdatedAt()).createdAt(po.getCreatedAt())
                .build();
    }

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

    @Override
    public void deleteSubRoleRelationsByRoleId(String roleId) {
        roleDao.deleteSubRoleRoleByRoleId(roleId);
    }

    @Override
    public void deleteUserAuditByRoleId(String roleId) {
        roleDao.deleteUserAuditByRoleId(roleId);
    }

    @Override
    public void deleteUserRoleByRoleId(String roleId) {
        roleDao.deleteUserRoleByRoleId(roleId);
    }

    @Override
    public void deleteUserLoginByRoleId(String roleId) {
        roleDao.deleteUserLoginByRoleId(roleId);
    }

    @Override
    public void deleteRoleSoftwareByRoleId(String roleId) {
        roleDao.deleteRoleSoftwareByRoleId(roleId);
    }

    @Override
    public void deleteById(String roleId) {
        roleDao.deleteById(roleId);
    }

    @Override
    public boolean existsByKeyOrName(String key, String name) {
        return roleDao.countByKeyOrName(key, name) > 0;
    }

    @Override
    public boolean existsByKeyExcludingId(String key, String id) {
        return roleDao.countByKeyExcludingId(key, id) > 0;
    }

    @Override
    public boolean existsByNameExcludingId(String name, String id) {
        return roleDao.countByNameExcludingId(name, id) > 0;
    }

    @Override
    public boolean existsByName(String name) {
        return roleDao.countByName(name) > 0;
    }

    @Override
    public List<String> queryRoleIdsByKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        java.util.Map<String, String> idByKey = new java.util.HashMap<>();
        for (RolePO row : roleDao.selectIdAndKeyByKeys(keys)) {
            idByKey.put(row.getKey(), row.getId());
        }
        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        for (String key : keys) {
            String id = idByKey.get(key);
            if (id != null) {
                ordered.add(id);
            }
        }
        return List.copyOf(ordered);
    }

    @Override
    public void insert(RoleEntity role) {
        roleDao.insert(toPO(role));
    }

    @Override
    public void update(RoleEntity role) {
        roleDao.update(toPO(role));
    }

    @Override
    @Transactional
    public void replaceUserRelationsByRoleId(String roleId, List<String> userIds) {
        roleDao.deleteUserRoleByRoleId(roleId);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String userId : new java.util.LinkedHashSet<>(userIds)) {
            // 厂商 `UserService.usersRelateRole:781` 对每个 userId 先 selectByPrimaryKey，查不到就 continue
            if (userDao.selectById(userId) == null) {
                continue;
            }
            roleDao.insertUserRole(userId, roleId, now);
            // 照抄厂商的"写错字段"：`users.setCreatedAt(now); updateByPrimaryKey(users)`（findings/156）
            userDao.touchCreatedAt(userId, now);
        }
    }

    @Override
    public List<com.csm.paas.core.domain.user.model.entity.UserEntity> queryFullUsersByRoleId(String roleId) {
        return userDao.selectAllByRoleId(roleId).stream().map(this::toUserEntity).toList();
    }

    @Override
    public void deleteUserRoleRelation(String roleId, String userId) {
        roleDao.deleteUserRoleByRoleIdAndUserId(roleId, userId);
    }

    @Override
    public void deleteSoftwareRelation(String roleId, String appId) {
        roleDao.deleteRoleSoftwareByRoleIdAndAppId(roleId, appId);
    }

    @Override
    @Transactional
    public void replaceSubRoleRelationsByRoleId(String roleId, List<String> subRoleIds) {
        roleDao.deleteSubRoleRoleByRoleId(roleId);
        if (subRoleIds == null || subRoleIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String subRoleId : new java.util.LinkedHashSet<>(subRoleIds)) {
            roleDao.insertSubRoleRole(subRoleId, roleId, now);
        }
    }

    @Override
    @Transactional
    public void replaceSubRoleRelationsTouchingCreatedAt(String roleId, List<String> subRoleIds) {
        roleDao.deleteSubRoleRoleByRoleId(roleId);
        if (subRoleIds == null || subRoleIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String subRoleId : new java.util.LinkedHashSet<>(subRoleIds)) {
            roleDao.insertSubRoleRole(subRoleId, roleId, now);
            // 厂商 `SubRoleServiceImpl.roleRelateSubRoles:417` 会把子角色的 created_at 覆盖成 now
            subRoleDao.touchCreatedAt(subRoleId, now);
        }
    }

    private com.csm.paas.core.domain.user.model.entity.UserEntity toUserEntity(
            com.csm.paas.core.infrastructure.dao.po.UserPO po) {
        return com.csm.paas.core.domain.user.model.entity.UserEntity.builder()
                .id(po.getId()).username(po.getUsername()).password(po.getPassword()).status(po.getStatus())
                .email(po.getEmail()).birth(po.getBirth()).phone(po.getPhone())
                .updatedAt(po.getUpdatedAt()).createdAt(po.getCreatedAt())
                .lastLoginRole(po.getLastLoginRole()).nickname(po.getNickname()).uuid(po.getUuid())
                .desc(po.getDesc()).avatar(po.getAvatar())
                .build();
    }

    private RolePO toPO(RoleEntity role) {
        RolePO po = new RolePO();
        po.setId(role.getId());
        po.setKey(role.getKey());
        po.setName(role.getName());
        po.setDesc(role.getDesc());
        po.setType(role.getType());
        po.setCreatedAt(role.getCreatedAt());
        po.setUpdatedAt(role.getUpdatedAt());
        return po;
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
