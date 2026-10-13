package com.csm.paas.core.cases.user.impl;

import com.csm.paas.core.cases.user.IUserUpdateCase;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;
import com.csm.paas.core.domain.user.service.IUserCommandService;
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
}
