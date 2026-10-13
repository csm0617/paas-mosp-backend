package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.infrastructure.dao.IRoleDao;
import com.csm.paas.core.infrastructure.dao.ISubRoleDao;
import com.csm.paas.core.infrastructure.dao.IUserDao;
import com.csm.paas.core.infrastructure.dao.po.UserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
