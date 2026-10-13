package com.csm.paas.core.cases.role.impl;

import com.csm.paas.core.cases.role.IRoleDeleteCase;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.service.IRoleCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 删战位的跨表编排 —— **事务边界在 Case 层**。
 *
 * <p>为什么必须在这一层：厂商的 `@Transactional` 打在包含 `forEach` 的整个方法上
 * （`RoleService.java:707`），也就是说"删三个战位，第三个是内置战位"要**整体回滚**。
 * 若把事务放在单表 DAO 或单个 repository 方法上，前两个会被提交——那就与厂商不一致了。
 *
 * <p>删除顺序照抄厂商（`RoleService.java:720-725`）：
 * `g_sub_role_role` → `g_user_audit` → `g_user_role` → `g_user_login` → `g_role_software` → `g_role`。
 * 其中 `g_user_role`/`g_sub_role_role` 有**无级联的外键**，顺序错了会被数据库直接拒绝
 * （见 `docs/findings/23-k1-live-schema-and-unique-constraints.md`）。
 *
 * <p><b>未实现</b>：厂商还会删 i18n 自动名（`deleteAutoNameMessage("role", "en_US"/"fr_FR", key)`）。
 * 本环境 `g_lang` 无覆盖数据，该调用是 no-op；等国际化域（切片 5）落地后回补。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleDeleteCaseImpl implements IRoleDeleteCase {

    private final IRoleCommandService roleCommandService;
    private final IRoleRepository roleRepository;

    @Override
    @Transactional
    public void deleteRoles(List<String> ids) {
        List<RoleEntity> deletable = roleCommandService.validateDeletable(ids);
        for (RoleEntity role : deletable) {
            String id = role.getId();
            roleRepository.deleteSubRoleRelationsByRoleId(id);
            roleRepository.deleteUserAuditByRoleId(id);
            roleRepository.deleteUserRoleByRoleId(id);
            roleRepository.deleteUserLoginByRoleId(id);
            roleRepository.deleteRoleSoftwareByRoleId(id);
            roleRepository.deleteById(id);
            log.info("已删除战位 id={} key={}", id, role.getKey());
        }
    }
}
