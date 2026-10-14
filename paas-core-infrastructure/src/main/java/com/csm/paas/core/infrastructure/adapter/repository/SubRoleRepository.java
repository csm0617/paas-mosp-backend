package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleRoleVO;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleUserVO;
import com.csm.paas.core.infrastructure.dao.ISubRoleDao;
import com.csm.paas.core.infrastructure.dao.po.SubRolePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SubRoleRepository implements ISubRoleRepository {

    private final ISubRoleDao subRoleDao;

    @Override
    public List<SubRoleEntity> queryPageOrderByCreatedAtDesc(int offset, int size) {
        return subRoleDao.selectPageOrderByCreatedAtDesc(offset, size).stream().map(this::toEntity).toList();
    }

    @Override
    public long count() {
        return subRoleDao.selectCount();
    }

    @Override
    public List<SubRoleEntity> queryByNameLike(String likeKeyword) {
        return subRoleDao.selectByNameLike(likeKeyword).stream().map(this::toEntity).toList();
    }

    @Override
    public List<SubRoleUserVO> queryUsersBySubRoleId(String subRoleId) {
        return subRoleDao.selectUsersBySubRoleId(subRoleId).stream()
                .map(po -> SubRoleUserVO.builder().userId(po.getUserId()).userName(po.getUserName()).build())
                .toList();
    }

    @Override
    public List<SubRoleRoleVO> queryRolesBySubRoleId(String subRoleId) {
        return subRoleDao.selectRolesBySubRoleId(subRoleId).stream()
                .map(po -> SubRoleRoleVO.builder()
                        .roleId(po.getRoleId()).roleKey(po.getRoleKey()).roleName(po.getRoleName())
                        .build())
                .toList();
    }

    @Override
    public List<SubRoleEntity> queryByUserId(String userId) {
        return subRoleDao.selectByUserId(userId).stream().map(this::toEntity).toList();
    }

    @Override
    public List<SubRoleEntity> queryByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return subRoleDao.selectByIds(ids).stream().map(this::toEntity).toList();
    }

    @Override
    public boolean existsInRoleByKeyOrName(String key, String name) {
        return subRoleDao.countRoleByKeyOrName(key, name) > 0;
    }

    @Override
    public boolean existsKeyExcludingId(String key, String id) {
        return subRoleDao.countByKeyExcludingId(key, id) > 0;
    }

    @Override
    public boolean existsNameExcludingId(String name, String id) {
        return subRoleDao.countByNameExcludingId(name, id) > 0;
    }

    @Override
    public void insertSubRole(String id, String key, String name, String desc, long updatedAt, long createdAt) {
        subRoleDao.insertSubRole(id, key, name, desc, updatedAt, createdAt);
    }

    @Override
    public int updateSubRoleSelective(String id, String key, String name, String desc, long updatedAt) {
        return subRoleDao.updateSubRoleSelective(id, key, name, desc, updatedAt);
    }

    @Override
    public void deleteUserSubRolesByUserId(String userId) {
        subRoleDao.deleteUserSubRolesByUserId(userId);
    }

    @Override
    public void insertUserSubRole(String id, String userId, String subRoleId, long createdAt, long updatedAt) {
        subRoleDao.insertUserSubRole(id, userId, subRoleId, createdAt, updatedAt);
    }

    @Override
    public boolean userExists(String userId) {
        return subRoleDao.countUserById(userId) > 0;
    }

    @Override
    public java.util.List<SubRoleEntity> queryForExport(java.util.List<String> ids) {
        return subRoleDao.selectForExport(ids).stream().map(this::toEntity).toList();
    }

    @Override
    public java.util.List<String> queryUsernamesBySubRoleId(String subRoleId) {
        return subRoleDao.selectUsernamesBySubRoleId(subRoleId);
    }

    @Override
    public java.util.List<String> queryRoleKeysBySubRoleId(String subRoleId) {
        return subRoleDao.selectRoleKeysBySubRoleId(subRoleId);
    }

    @Override
    public java.util.Optional<SubRoleEntity> queryById(String id) {
        return java.util.Optional.ofNullable(subRoleDao.selectById(id)).map(this::toEntity);
    }

    @Override
    public void deleteById(String id) {
        subRoleDao.deleteById(id);
    }

    @Override
    public void deleteSubRoleRolesBySubRoleId(String subRoleId) {
        subRoleDao.deleteSubRoleRolesBySubRoleId(subRoleId);
    }

    @Override
    public void deleteUserSubRolesBySubRoleId(String subRoleId) {
        subRoleDao.deleteUserSubRolesBySubRoleId(subRoleId);
    }

    @Override
    public java.util.Optional<SubRoleEntity> queryByKey(String key) {
        return java.util.Optional.ofNullable(subRoleDao.selectByKey(key)).map(this::toEntity);
    }

    private SubRoleEntity toEntity(SubRolePO po) {
        return SubRoleEntity.builder()
                .id(po.getId()).key(po.getKey()).name(po.getName()).desc(po.getDesc())
                .updatedAt(po.getUpdatedAt()).createdAt(po.getCreatedAt())
                .build();
    }
}
