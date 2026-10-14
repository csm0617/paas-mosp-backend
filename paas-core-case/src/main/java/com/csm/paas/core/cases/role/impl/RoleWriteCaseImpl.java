package com.csm.paas.core.cases.role.impl;

import com.csm.paas.core.cases.role.IRoleWriteCase;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleUsersEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleFormVO;
import com.csm.paas.core.domain.role.service.IRoleCommandService;
import com.csm.paas.core.domain.software.adapter.port.ISoftwareRelationBroadcastPort;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 战位新增/修改的事务编排（规则见 {@link IRoleWriteCase}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleWriteCaseImpl implements IRoleWriteCase {

    private static final String ADMIN_KEY = "admin";
    private static final String ADMIN_USERNAME = "admin";

    private final IRoleCommandService roleCommandService;
    private final IRoleRepository roleRepository;
    private final IUserRepository userRepository;
    private final ISubRoleRepository subRoleRepository;
    private final ISoftwareRelationBroadcastPort softwareRelationBroadcastPort;

    @Override
    @Transactional
    public RoleEntity addRole(RoleFormVO form) {
        RoleEntity role = roleCommandService.prepareNewRole(form);
        roleRepository.insert(role);

        relateUsers(role, form.getUserList());
        relateSubRoles(role, form.getSubRoleIdList());

        log.info("已新增战位 id={} key={} name={}", role.getId(), role.getKey(), role.getName());
        return roleRepository.queryById(role.getId()).orElse(role);
    }

    @Override
    @Transactional
    public RoleEntity editRole(String id, RoleFormVO form) {
        RoleEntity updated = roleCommandService.prepareEditedRole(id, form);
        roleRepository.update(updated);
        log.info("已修改战位 id={} key={}", id, updated.getKey());
        return roleRepository.queryById(id).orElse(updated);
    }

    /** `userList` 是**用户名**列表；`admin` 战位还会强制带上 `admin` 用户（厂商 `usersRelateRole`）。 */
    private void relateUsers(RoleEntity role, List<String> usernames) {
        List<String> userIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(usernames)) {
            for (String username : new LinkedHashSet<>(usernames)) {
                userRepository.queryByUsername(username).ifPresent(u -> userIds.add(u.getId()));
            }
        }
        if (ADMIN_KEY.equals(role.getKey())) {
            userRepository.queryByUsername(ADMIN_USERNAME).ifPresent(u -> userIds.add(u.getId()));
        }
        roleRepository.replaceUserRelationsByRoleId(role.getId(), userIds);
    }

    /** `subRoleIdList` 是子角色 **id** 列表；不存在的跳过（厂商逐个 `select`）。 */
    private void relateSubRoles(RoleEntity role, List<String> subRoleIds) {
        if (CollectionUtils.isEmpty(subRoleIds)) {
            roleRepository.replaceSubRoleRelationsByRoleId(role.getId(), List.of());
            return;
        }
        List<String> existing = subRoleRepository.queryByIds(new ArrayList<>(new LinkedHashSet<>(subRoleIds)))
                .stream().map(SubRoleEntity::getId).toList();
        roleRepository.replaceSubRoleRelationsByRoleId(role.getId(), existing);
    }

    // ---------- 关联写（四个端点；规则在 IRoleCommandService，这里只加事务与推送）----------

    @Override
    @Transactional
    public RoleUsersEntity relateUsers(String roleId, List<String> userIds) {
        return roleCommandService.relateUsers(roleId, userIds);
    }

    @Override
    @Transactional
    public RoleEntity relateSubRoles(String roleId, List<String> subRoleIds) {
        return roleCommandService.relateSubRoles(roleId, subRoleIds);
    }

    @Override
    @Transactional
    public RoleUsersEntity removeUserFromRole(String roleId, String userId) {
        return roleCommandService.removeUserFromRole(roleId, userId);
    }

    /**
     * 厂商 `RoleService.roleRemoveApp:732-738`：删关联**之后**往 `/topic/software` 推
     * `{event:"DELETE", roleList:roleId}`。推送失败不影响主流程（见实现类注释）。
     */
    @Override
    @Transactional
    public void removeAppFromRole(String roleId, String appId) {
        roleCommandService.removeAppFromRole(roleId, appId);
        softwareRelationBroadcastPort.softwareRelationDeleted(roleId);
    }
}
