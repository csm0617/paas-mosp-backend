package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.infrastructure.dao.IRoleDao;
import com.csm.paas.core.infrastructure.dao.ISubRoleDao;
import com.csm.paas.core.infrastructure.dao.IUserDao;
import com.csm.paas.core.infrastructure.dao.po.UserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.csm.paas.core.domain.user.model.valobj.UserLoginItemVO;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepository implements IUserRepository {

    private final IUserDao userDao;
    private final IRoleDao roleDao;
    private final ISubRoleDao subRoleDao;

    @Override
    public Optional<UserEntity> queryByUsername(String username) {
        return Optional.ofNullable(userDao.selectByUsername(username)).map(this::toEntity);
    }

    @Override
    public List<UserLoginItemVO> queryLoginList() {
        return userDao.selectLoginList().stream()
                .map(po -> UserLoginItemVO.builder()
                        .username(po.getUsername())
                        .lastLoginRole(po.getLastLoginRole())
                        .avatar(po.getAvatar())
                        .build())
                .toList();
    }

    @Override
    public long count() {
        return userDao.selectCount();
    }

    @Override
    public List<UserEntity> queryPageByKeyword(String likeKeyword, int offset, int size) {
        return userDao.selectPageByKeyword(likeKeyword, offset, size).stream().map(this::toEntity).toList();
    }

    @Override
    public long countByKeyword(String likeKeyword) {
        return userDao.selectCountByKeyword(likeKeyword);
    }

    @Override
    public List<String> queryRoleNamesByUsernameAndType(String username, int type) {
        return roleDao.selectRoleNamesByUsernameAndType(username, type);
    }

    @Override
    public List<String> querySubRoleNamesByUserId(String userId) {
        return subRoleDao.selectNamesByUserId(userId);
    }

    @Override
    public java.util.Optional<UserEntity> queryById(String id) {
        return java.util.Optional.ofNullable(userDao.selectById(id)).map(this::toEntity);
    }

    @Override
    public List<UserEntity> queryAll() {
        return userDao.selectAll().stream().map(this::toEntity).toList();
    }

    @Override
    public List<String> queryRoleKeysByUsername(String username) {
        return userDao.selectRoleKeysByUsername(username);
    }

    /**
     * 用户删除的级联：`g_user_role` → `g_user_sub_role` → `g_user`，**一个事务**。
     * <p>厂商的 `UserService.deleteUser` 打了 `@Transactional`（`UserService.java:820`）；
     * 事务放在 adapter 里是因为**这里就是原子的边界**（没有跨用户批次的原子性要求，
     * 见 `UserController.deleteAllUser` 是逐 id 调用）。
     */
    @Override
    @Transactional
    public void deleteUserCascade(String userId) {
        userDao.deleteUserRoleByUserId(userId);
        userDao.deleteUserSubRoleByUserId(userId);
        userDao.deleteById(userId);
    }

    private UserEntity toEntity(UserPO po) {
        return UserEntity.builder()
                .id(po.getId()).username(po.getUsername()).password(po.getPassword()).status(po.getStatus())
                .email(po.getEmail()).birth(po.getBirth()).phone(po.getPhone())
                .updatedAt(po.getUpdatedAt()).createdAt(po.getCreatedAt())
                .lastLoginRole(po.getLastLoginRole()).nickname(po.getNickname()).uuid(po.getUuid())
                .desc(po.getDesc()).avatar(po.getAvatar())
                .build();
    }
}
