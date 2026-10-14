package com.csm.paas.core.domain.role.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleOnlineUserRepository;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleUsersEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleFormVO;
import com.csm.paas.core.domain.role.service.IRoleCommandService;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 战位删除的前置校验（规则见 {@link IRoleCommandService} 的文档）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleCommandServiceImpl implements IRoleCommandService {

    private static final String ADMIN_KEY = "admin";
    private static final String DEV_KEY = "dev";
    private static final String ADMIN_USERNAME = "admin";

    private final IRoleRepository roleRepository;
    private final IUserRepository userRepository;
    private final ISubRoleRepository subRoleRepository;
    private final IRoleOnlineUserRepository onlineUserRepository;

    @Override
    public List<RoleEntity> validateDeletable(List<String> ids) {
        List<RoleEntity> deletable = new ArrayList<>();
        if (ids == null) {
            return deletable;
        }
        for (String id : ids) {
            RoleEntity role = roleRepository.queryById(id)
                    .orElseThrow(() -> new WrongArgumentException("role.missing.storage"));
            String key = role.getKey();
            if (ADMIN_KEY.equals(key) || DEV_KEY.equals(key)) {
                log.debug("无法删除当前战位：{}", key);
                throw new WrongArgumentException("role.cannot.delete.storage");
            }
            deletable.add(role);
        }
        return deletable;
    }

    // ---------- 新增 / 修改（规则见 IRoleCommandService 的文档）----------

    @Override
    public RoleEntity prepareNewRole(RoleFormVO form) {
        // 控制器：key **或** name 已存在就拒（注意是 OR）
        if (roleRepository.existsByKeyOrName(form.getKey(), form.getName())) {
            throw new WrongArgumentException("role.storage.existed", form.getKey(), form.getName());
        }
        if (form.getName() == null || form.getName().length() > 100 || form.getName().length() < 1) {
            throw new WrongArgumentException("role.name.invalid.length");
        }
        // 厂商写的是 `key.length() < 0`（不是 <1）⇒ **空 key 也能过**；这里照抄上界
        if (form.getKey() != null && (form.getKey().length() > 36 || form.getKey().length() < 0)) {
            throw new WrongArgumentException("role.unique.identifier.invalid.length");
        }
        if (form.getDesc() != null && form.getDesc().length() > 255) {
            throw new WrongArgumentException("role.desc.invalid.length");
        }

        return RoleEntity.builder()
                .id(UUID.randomUUID().toString())
                .key(form.getKey())
                .name(form.getName())
                .desc(StringUtils.hasText(form.getDesc()) ? form.getDesc() : null)
                .type(form.getType())
                .createdAt(System.currentTimeMillis())
                // 厂商 addRole **不设 updatedAt**（RoleService.java:552）⇒ 新建战位的 updatedAt 是 null
                .build();
    }

    @Override
    public RoleEntity prepareEditedRole(String id, RoleFormVO form) {
        RoleEntity existing = roleRepository.queryById(id)
                .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));

        boolean isAdmin = ADMIN_KEY.equals(existing.getKey());
        boolean isDev = DEV_KEY.equals(existing.getKey());
        // **只要表单带了 key**（哪怕与当前 key 相同）就不许改内置战位
        if (form.getKey() != null && isAdmin) {
            throw new WrongArgumentException("role.can.not.edit.admin.key");
        }
        if (form.getKey() != null && isDev) {
            throw new WrongArgumentException("role.can.not.edit.dev.key");
        }
        if (ADMIN_KEY.equals(form.getKey()) || DEV_KEY.equals(form.getKey())) {
            throw new WrongArgumentException("role.identifier.reserved.word", form.getKey());
        }
        if (form.getKey() != null && roleRepository.existsByKeyExcludingId(form.getKey(), id)) {
            throw new WrongArgumentException("role.key.exist");
        }
        if (form.getName() != null && roleRepository.existsByNameExcludingId(form.getName(), id)) {
            throw new WrongArgumentException("role.change.name.exist");
        }
        if (form.getName() != null && (form.getName().length() > 100 || form.getName().length() < 1)) {
            throw new WrongArgumentException("role.change.name.invalid");
        }
        if (form.getKey() != null && (form.getKey().length() > 36 || form.getKey().length() < 1)) {
            throw new WrongArgumentException("role.change.key.name.invalid");
        }
        if (form.getDesc() != null && form.getDesc().length() > 255) {
            throw new WrongArgumentException("role.change.desc.invalid");
        }

        // 厂商用 updateByPrimaryKeySelective ⇒ 表单为 null 的字段**不更新**
        return RoleEntity.builder()
                .id(existing.getId())
                .key(form.getKey() != null ? form.getKey() : existing.getKey())
                .name(form.getName() != null ? form.getName() : existing.getName())
                // 修改时 desc 空白置 **空串**（新增时置 null）
                .desc(StringUtils.hasText(form.getDesc()) ? form.getDesc() : "")
                .type(form.getType() != null ? form.getType() : existing.getType())
                .createdAt(existing.getCreatedAt())
                .updatedAt(System.currentTimeMillis())
                .build();
    }

    // ---------- 关联写（四个端点，规则见 IRoleCommandService 的文档）----------

    @Override
    public RoleUsersEntity relateUsers(String roleId, List<String> userIds) {
        RoleEntity role = roleRepository.queryById(roleId)
                .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
        List<String> ids = userIds == null ? new ArrayList<>() : new ArrayList<>(userIds);
        if (ADMIN_KEY.equals(role.getKey())) {
            // 厂商：admin 战位强制带上 admin 用户（UserService.usersRelateRole:774-776）
            userRepository.queryByUsername(ADMIN_USERNAME).ifPresent(u -> ids.add(u.getId()));
        }
        roleRepository.replaceUserRelationsByRoleId(roleId, ids);
        log.info("战位 {} 关联用户 {} 个", roleId, ids.size());
        return roleUsers(roleId, role);
    }

    @Override
    public RoleEntity relateSubRoles(String roleId, List<String> subRoleIds) {
        roleRepository.queryById(roleId)
                .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
        List<String> existing = subRoleIds == null ? List.of()
                : subRoleRepository.queryByIds(new ArrayList<>(new java.util.LinkedHashSet<>(subRoleIds)))
                .stream().map(SubRoleEntity::getId).toList();
        // 注意用**带 created_at 覆盖**的那个重载：厂商这条路径走 roleRelateSubRoles
        roleRepository.replaceSubRoleRelationsTouchingCreatedAt(roleId, existing);
        log.info("战位 {} 关联子角色 {} 个", roleId, existing.size());
        return roleWithSubRoles(roleId);
    }

    @Override
    public RoleUsersEntity removeUserFromRole(String roleId, String userId) {
        RoleEntity role = roleRepository.queryById(roleId)
                .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
        // 厂商这里是 `userMapper.selectByPrimaryKey(userId).get()`（查不到抛 NoSuchElementException ⇒ 500）
        var user = userRepository.queryById(userId)
                .orElseThrow(() -> new WrongArgumentException("user.id.not.found"));
        if (ADMIN_USERNAME.equals(user.getUsername()) && ADMIN_KEY.equals(role.getKey())) {
            throw new WrongArgumentException("user.cannot.remove.ofc.admin");
        }
        onlineUserRepository.evictSessionIfOnline(user.getUsername(), role.getKey());
        roleRepository.deleteUserRoleRelation(roleId, userId);
        log.info("已把用户 {} 从战位 {} 移除", userId, roleId);
        return roleUsers(roleId, role);
    }

    @Override
    public void removeAppFromRole(String roleId, String appId) {
        roleRepository.queryById(roleId)
                .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
        roleRepository.deleteSoftwareRelation(roleId, appId);
        log.info("战位 {} 解除关联软件 {}", roleId, appId);
    }

    /** 重新查 `RoleUsers`：战位标量（重查一次，与厂商 `userRolesByRoleId` 一致）+ 全量用户。 */
    private RoleUsersEntity roleUsers(String roleId, RoleEntity fallback) {
        return RoleUsersEntity.builder()
                .role(roleRepository.queryById(roleId).orElse(fallback))
                .list(roleRepository.queryFullUsersByRoleId(roleId))
                .build();
    }

    /** 重新查"战位 + 子角色列表"（厂商 `SubRoleServiceImpl.subRolesByRoleId:426-434`）。 */
    private RoleEntity roleWithSubRoles(String roleId) {
        RoleEntity role = roleRepository.queryById(roleId)
                .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
        role.setSubRoleList(subRoleRepository.queryByIds(roleRepository.querySubRoleIdsByRoleId(roleId)));
        return role;
    }
}
