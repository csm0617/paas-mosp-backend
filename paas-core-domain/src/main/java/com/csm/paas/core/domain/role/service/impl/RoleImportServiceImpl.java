package com.csm.paas.core.domain.role.service.impl;

import com.csm.paas.core.domain.language.adapter.port.ILanguageAutoNamePort;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.valobj.ImportRoleRowVO;
import com.csm.paas.core.domain.role.service.IRoleImportService;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 战位导入的落库实现（规则见 {@link IRoleImportService} 的文档）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleImportServiceImpl implements IRoleImportService {

    private static final String USER_KEY = "user";
    private static final int DESC_MAX_LENGTH = 255;

    private final IRoleRepository roleRepository;
    private final IUserRepository userRepository;
    private final ISubRoleRepository subRoleRepository;
    private final ISoftwareRepository softwareRepository;
    private final ILanguageAutoNamePort autoNamePort;

    /** 待插入的新战位：战位行 + 该行的关联列表（厂商 `ImportRole` 同时带这两部分）。 */
    private record Pending(RoleEntity role, ImportRoleRowVO row) {
    }

    @Override
    public void importRoles(List<ImportRoleRowVO> rows, boolean isCover) {
        if (rows == null || rows.isEmpty()) {
            // 厂商：`lastRowNum < 1` ⇒ 记一条恢复日志后 return true（HTTP 200，库无变化）
            log.info("战位表格无内容，或内容不匹配,直接返回成功");
            return;
        }
        long now = System.currentTimeMillis();
        List<Pending> pending = new ArrayList<>();

        for (ImportRoleRowVO row : rows) {
            String key = row.getKey();
            if (key == null) {
                throw new WrongArgumentException("role.key.is.null.or.format.error");
            }
            if (key.length() > 36 || key.length() < 1) {
                throw new WrongArgumentException("role.key.is.illegal", key);
            }
            String name = row.getName();
            if (name == null) {
                throw new WrongArgumentException("role.name.is.null.or.file.format.error");
            }
            if (name.length() > 100 || name.length() < 1) {
                throw new WrongArgumentException("role.name.is.illegal", name);
            }
            // 厂商这里还有一句判空（role.import.role.key.not.valid），但上面两条长度检查已经把空串拦掉了
            if (key.isEmpty() || name.isEmpty()) {
                throw new WrongArgumentException("role.import.role.key.not.valid", row.getRowIndex());
            }

            if (roleRepository.existsByKeyOrName(key, name)) {
                if (!isCover) {
                    continue;
                }
                // 厂商 `roles.get(0)` 不判空 ⇒ 查不到会 IndexOutOfBounds（我们这里不可能查不到：
                // existsByKeyOrName 刚判定过；真查不到就按 400 处理）
                RoleEntity role = roleRepository.queryByKeyOrName(key, name)
                        .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
                roleRepository.deleteUserRoleByRoleId(role.getId());
                roleRepository.deleteRoleSoftwareByRoleId(role.getId());

                String desc = row.getDesc();
                if (desc != null && desc.length() > DESC_MAX_LENGTH) {
                    throw new WrongArgumentException("role.invalid.storage.description.length");
                }
                // 厂商 `role.setDesc(desc); updateByPrimaryKey(role)`：**整行** update，desc=null 就写成 NULL
                role.setDesc(desc);
                roleRepository.update(role);

                relateUsers(role.getId(), row.getUsernames(), now);
                relateSoftware(role.getId(), row.getSoftwareKeys(), now);
                if (row.getSubRoleKeys() == null || row.getSubRoleKeys().isEmpty()) {
                    // 厂商：子角色列为空 ⇒ `continue`（**跳过自动名**）
                    continue;
                }
                relateSubRoles(role.getId(), row.getSubRoleKeys(), now);
                // 厂商成功处理子角色后也是 `continue`（同样跳过自动名）
                continue;
            }

            RoleEntity fresh = RoleEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .key(key)
                    .name(name)
                    .desc(row.getDesc())
                    .type(1)
                    .createdAt(now)
                    .build();
            pending.add(new Pending(fresh, row));
            saveAutoNames(key, row);
        }

        for (Pending item : pending) {
            RoleEntity role = item.role();
            roleRepository.insert(role);
            // 厂商插完再 `select(key = ?)` 取回 id
            String roleId = roleRepository.queryByKey(role.getKey()).map(RoleEntity::getId).orElse(role.getId());
            ImportRoleRowVO row = item.row();
            relateUsers(roleId, row.getUsernames(), now);
            relateSoftware(roleId, row.getSoftwareKeys(), now);
            relateSubRoles(roleId, row.getSubRoleKeys(), now);
        }
        log.info("战位导入完成：新增 {} 个（isCover={}）", pending.size(), isCover);
    }

    /** 写两个自动名（厂商 `saveAutoNameMessage`：值为 null ⇒ 不写）。 */
    private void saveAutoNames(String key, ImportRoleRowVO row) {
        if (row.getNameUs() != null) {
            autoNamePort.saveAutoName("role", "en_US", key, row.getNameUs());
        }
        if (row.getNameFr() != null) {
            autoNamePort.saveAutoName("role", "fr_FR", key, row.getNameFr());
        }
    }

    /**
     * 按用户名列表写"用户-战位"关联（厂商 `importRoleExcel:903-935` / `1061-1090`）。
     * <p>每个用户：查不到就跳过；若该用户**还没有 `user` 战位**，先补一条；
     * 再插一条本战位的关联（**不去重**，因为覆盖分支刚删光过、新战位本来就没有）。
     */
    private void relateUsers(String roleId, List<String> usernames, long now) {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }
        for (String username : usernames) {
            var user = userRepository.queryByUsername(username);
            if (user.isEmpty()) {
                continue;
            }
            String userId = user.get().getId();
            if (!userRepository.queryRoleKeysByUsername(username).contains(USER_KEY)) {
                RoleEntity userRole = roleRepository.queryByKey(USER_KEY)
                        .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
                userRepository.insertUserRoleRelation(userId, userRole.getId(), now);
            }
            userRepository.insertUserRoleRelation(userId, roleId, now);
        }
    }

    /** 按软件 `key` 列表写"战位-软件"关联，`order` 从 1 递增（厂商 `:938-965` / `1092-1105`）。 */
    private void relateSoftware(String roleId, List<String> softwareKeys, long now) {
        if (softwareKeys == null || softwareKeys.isEmpty()) {
            return;
        }
        int order = 1;
        for (String key : softwareKeys) {
            var softwareId = softwareRepository.queryIdByKey(key);
            if (softwareId.isEmpty()) {
                continue;
            }
            roleRepository.insertRoleSoftware(roleId, softwareId.get(), order++, now);
        }
    }

    /** 按子角色 `key` 列表**替换**关联（厂商：先删该战位的全部子角色关联再逐条插）。 */
    private void relateSubRoles(String roleId, List<String> subRoleKeys, long now) {
        if (subRoleKeys == null || subRoleKeys.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String key : subRoleKeys) {
            subRoleRepository.queryByKey(key).ifPresent(sr -> ids.add(sr.getId()));
        }
        roleRepository.replaceSubRoleRelationsByRoleId(roleId, ids);
    }
}
