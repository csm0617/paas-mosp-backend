package com.csm.paas.core.cases.user.impl;

import com.csm.paas.core.cases.user.IUserAddCase;
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

/** 新增用户的事务编排（规则见 {@link IUserAddCase}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddCaseImpl implements IUserAddCase {

    /** 战位（`g_role.type` = 1）。 */
    private static final int ROLE_TYPE_POSITION = 1;
    /** 角色（`g_role.type` = 2）。 */
    private static final int ROLE_TYPE_ROLE = 2;

    private final IUserCommandService userCommandService;
    private final IUserRepository userRepository;

    @Override
    @Transactional
    public UserEntity addUser(UserFormVO form) {
        UserEntity user = userCommandService.prepareNewUser(form);
        userRepository.insert(user);

        // 厂商顺序：userListKey（角色 type=2）→ roleListKey（战位 type=1）→ subRoleListId
        relateByKeys(user.getId(), ROLE_TYPE_ROLE, form.getUserListKey());
        relateByKeys(user.getId(), ROLE_TYPE_POSITION, form.getRoleListKey());
        if (!CollectionUtils.isEmpty(form.getSubRoleListId())) {
            userRepository.replaceUserSubRoleRelations(user.getId(), form.getSubRoleListId());
        }

        log.info("已新增用户 id={} username={} uuid={}", user.getId(), user.getUsername(), user.getUuid());
        return user;
    }

    private void relateByKeys(String userId, int type, List<String> roleKeys) {
        if (CollectionUtils.isEmpty(roleKeys)) {
            // 厂商对 null 会 NPE（缺陷），这里按空处理；新建用户本来也没有旧关联要清
            return;
        }
        List<String> roleIds = userRepository.queryRoleIdsByKeys(roleKeys);
        userRepository.replaceUserRoleRelations(userId, type, roleIds);
    }
}
