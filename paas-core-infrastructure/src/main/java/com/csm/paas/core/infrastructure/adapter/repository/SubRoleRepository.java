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
