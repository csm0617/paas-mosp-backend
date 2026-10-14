package com.csm.paas.core.domain.subrole.service.impl;

import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.service.ISubRoleCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 子角色写操作（规格见 `docs/findings/147`）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubRoleCommandServiceImpl implements ISubRoleCommandService {

    private final ISubRoleRepository subRoleRepository;

    private static final int MAX_NAME = 100;
    private static final int MAX_KEY = 36;
    private static final int MAX_DESC = 255;

    @Override
    public com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity addSubRole(
            com.csm.paas.core.domain.subrole.model.valobj.SubRoleFormVO form) {
        if (form == null) {
            return null;
        }
        // 厂商：重名检查查的是 **g_role**（战位表）
        if (subRoleRepository.existsInRoleByKeyOrName(form.getKey(), form.getName())) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "sub.role.storage.existed", form.getKey(), form.getName());
        }
        String name = form.getName();
        if (name == null || name.isEmpty() || name.length() > MAX_NAME) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("sub.role.name.invalid.length");
        }
        String key = form.getKey();
        if (key == null || key.isEmpty() || key.length() > MAX_KEY) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "sub.role.unique.identifier.invalid.length");
        }
        String desc = form.getDesc();
        if (desc != null && desc.isBlank()) {
            desc = null;
        }
        if (desc != null && desc.length() > MAX_DESC) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("sub.role.desc.invalid.length");
        }
        long now = System.currentTimeMillis();
        String id = java.util.UUID.randomUUID().toString();
        subRoleRepository.insertSubRole(id, key, name, desc, now, now);
        if (form.getUserList() != null && !form.getUserList().isEmpty()) {
            relateUsers(id, form.getUserList());
        }
        return subRoleRepository.queryById(id).orElse(null);
    }

    @Override
    public boolean updateSubRole(com.csm.paas.core.domain.subrole.model.valobj.SubRoleFormVO form) {
        String id = form.getId();
        // 厂商 getSuRoleById 在"不存在"时会 NPE ⇒ 500；我们按它本来想表达的 400 处理（deviations B24）
        com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity existing =
                subRoleRepository.queryById(id).orElseThrow(
                        () -> new com.csm.paas.core.types.exception.WrongArgumentException(
                                "sub.role.storage.not.found"));
        if (form.getKey() != null && subRoleRepository.existsKeyExcludingId(form.getKey(), id)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("sub.role.key.exist");
        }
        if (form.getName() != null && subRoleRepository.existsNameExcludingId(form.getName(), id)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("sub.role.change.name.exist");
        }
        String name = form.getName();
        if (name != null && (name.length() > MAX_NAME || name.length() < 1)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("sub.role.change.name.invalid");
        }
        String key = form.getKey();
        if (key != null && (key.length() > MAX_KEY || key.length() < 1)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("sub.role.change.key.name.invalid");
        }
        String desc = form.getDesc();
        if (desc != null && desc.isBlank()) {
            desc = null;
        }
        if (desc != null && desc.length() > MAX_DESC) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("sub.role.change.desc.invalid");
        }
        long now = System.currentTimeMillis();
        // 厂商：`LinkedHashSet.addAll(userList)` 对 null 会 NPE；我们按空列表处理（deviations B24）
        relateUsers(id, form.getUserList() == null ? java.util.List.of() : form.getUserList());
        log.info("更新子角色 {}（原 key={}）", id, existing.getKey());
        return subRoleRepository.updateSubRoleSelective(id, key, name, desc, now) > 0;
    }

    @Override
    public boolean userAddRole(String userId, java.util.List<String> subRoleIdList) {
        subRoleRepository.deleteUserSubRolesByUserId(userId);
        long now = System.currentTimeMillis();
        for (String subRoleId : subRoleIdList == null ? java.util.List.<String>of() : subRoleIdList) {
            // 厂商：不校验子角色是否存在
            subRoleRepository.insertUserSubRole(java.util.UUID.randomUUID().toString(), userId, subRoleId, now, now);
        }
        return true;
    }

    @Override
    public boolean roleAddUser(String subRoleId, java.util.List<String> userIdList) {
        subRoleRepository.deleteUserSubRolesBySubRoleId(subRoleId);
        long now = System.currentTimeMillis();
        for (String userId : userIdList == null ? java.util.List.<String>of() : userIdList) {
            // 厂商：**不校验用户是否存在**（与 usersRelateRole 不同）
            subRoleRepository.insertUserSubRole(java.util.UUID.randomUUID().toString(), userId, subRoleId, now, now);
        }
        return true;
    }

    /**
     * 厂商 `usersRelateRole:144-160`：**先删光该子角色的用户关联**，再逐个 userId 关联
     * （**用户不存在则跳过**；用 `HashSet` 去重 ⇒ 顺序不确定）。
     */
    private void relateUsers(String subRoleId, java.util.List<String> userIds) {
        subRoleRepository.deleteUserSubRolesBySubRoleId(subRoleId);
        long now = System.currentTimeMillis();
        for (String userId : new java.util.HashSet<>(userIds)) {
            if (!subRoleRepository.userExists(userId)) {
                continue;
            }
            subRoleRepository.insertUserSubRole(java.util.UUID.randomUUID().toString(), userId, subRoleId, now, now);
        }
    }

    @Override
    public boolean deleteSubRoles(List<String> subRoleIds) {
        if (subRoleIds == null) {
            return true;
        }
        for (String subRoleId : subRoleIds) {
            // 厂商：key 非空白时删 i18n 自动名（本环境无 i18n 覆盖机制，跳过）
            subRoleRepository.queryById(subRoleId)
                    .filter(role -> role.getKey() != null && !role.getKey().isBlank())
                    .ifPresent(role -> log.info("删除子角色 {} 的 i18n 自动名（本环境未实现 i18n 覆盖）", role.getKey()));
            subRoleRepository.deleteSubRoleRolesBySubRoleId(subRoleId);
            subRoleRepository.deleteById(subRoleId);
            subRoleRepository.deleteUserSubRolesBySubRoleId(subRoleId);
        }
        return true;
    }
}
