package com.csm.paas.core.cases.user.impl;

import com.csm.paas.core.cases.user.IUserUpdateCase;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;
import com.csm.paas.core.domain.user.service.IUserCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

/** 修改用户的事务编排（规则见 {@link IUserUpdateCase}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserUpdateCaseImpl implements IUserUpdateCase {

    private static final int ROLE_TYPE_POSITION = 1;
    private static final int ROLE_TYPE_ROLE = 2;

    private final IUserCommandService userCommandService;
    private final IUserRepository userRepository;
    private final com.csm.paas.core.domain.role.adapter.repository.IRoleRepository roleRepository;

    @Override
    @Transactional
    public UserEntity updateUser(String userId, UserFormVO form) {
        UserEntity updated = userCommandService.prepareUpdatedUser(userId, form);
        userRepository.update(updated);

        // 与"新增"相反：**空即删除**（厂商 :636-655）
        replaceOrClear(userId, ROLE_TYPE_ROLE, form.getUserListKey());
        replaceOrClear(userId, ROLE_TYPE_POSITION, form.getRoleListKey());
        if (CollectionUtils.isEmpty(form.getSubRoleListId())) {
            userRepository.replaceUserSubRoleRelations(userId, List.of());
        } else {
            userRepository.replaceUserSubRoleRelations(userId, form.getSubRoleListId());
        }

        log.info("已修改用户 id={} username={}", updated.getId(), updated.getUsername());
        return updated;
    }

    private void replaceOrClear(String userId, int type, List<String> roleKeys) {
        if (CollectionUtils.isEmpty(roleKeys)) {
            userRepository.replaceUserRoleRelations(userId, type, List.of());
            return;
        }
        userRepository.replaceUserRoleRelations(userId, type, userRepository.queryRoleIdsByKeys(roleKeys));
    }

    @Override
    @Transactional
    public boolean replaceUserRoles(String userId, List<String> roleIdList) {
        userRepository.queryById(userId)
                .orElseThrow(() -> new WrongArgumentException("user.missing.user.info"));
        // 厂商用 HashSet 去重（顺序不确定）；这里同样去重，但保留入参顺序以便可复现
        List<String> deduped = roleIdList == null ? List.of()
                : List.copyOf(new java.util.LinkedHashSet<>(roleIdList));
        userRepository.replaceAllUserRoleRelations(userId, deduped);
        log.info("已替换用户 {} 的全部战位/角色关联，共 {} 条", userId, deduped.size());
        return true;
    }

    @Override
    @Transactional
    public void relatePositions(String userId, List<String> roleIdList) {
        if (userRepository.queryById(userId).isEmpty()) {
            throw new WrongArgumentException("user.not.found");
        }
        // 注意：这个端点的入参是 **roleId**（不是 key）——厂商直接把它交给 `rolesRelate2User(userId, 1, ids)`，
        // 而 `rolesRelate2User` 会逐个 selectByPrimaryKey，找不到就跳过（UserService.java:798-799）
        List<String> ids = roleIdList == null ? List.of() : roleIdList;
        userRepository.replaceUserRoleRelations(userId, ROLE_TYPE_POSITION,
                roleRepository.filterExistingIds(ids));
    }
}
