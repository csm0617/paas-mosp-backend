package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 用户导出数据（厂商 `UserService.generateExportExcel:977-1026`）。
 *
 * <p>**8 列**（照抄）：`username` / `nickname` / **第一个**角色 key / 角色关系 key 列表 **JSON** /
 * 子角色 key 列表 **JSON** / `desc` / `status`(int) / **`password`（口令哈希）**。
 * <p>**跳过 `admin` 与 `root`**（注意：**不跳 `dev`**，与 `generateBackupExcel` 不同）；
 * 查询按 `created_at DESC`；列表为空 ⇒ 全部用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserExportService {

    private static final int ROLE_TYPE_POSITION = 1;
    private static final int ROLE_TYPE_ROLE = 2;
    private static final List<String> SKIPPED = List.of("admin", "root");

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final ISubRoleRepository subRoleRepository;

    /**
     * 导出行（顺序即 Excel 列序）。
     * <p>角色/子角色以**键列表**返回（不在这里转 JSON）：domain 模块没有 Jackson，
     * 序列化交给 trigger 层的写出器（本会话早前踩过同一个坑）。
     */
    public record Row(String username, String nickname, String roleKey, List<String> roleKeys,
                      List<String> subRoleKeys, String desc, Integer status, String password) {
    }

    public List<Row> exportRows(List<String> userIdList) {
        List<String> ids = userIdList == null ? List.of() : userIdList;
        List<UserEntity> users = userRepository.queryPageByKeyword(null, 0, Integer.MAX_VALUE).stream()
                .filter(u -> ids.isEmpty() || ids.contains(u.getId()))
                .filter(u -> !SKIPPED.contains(u.getUsername()))
                .toList();
        List<Row> rows = users.stream().map(this::toRow).toList();
        log.info("用户导出：候选 {} 行，跳过 admin/root 后 {} 行", users.size(), rows.size());
        return rows;
    }

    private Row toRow(UserEntity u) {
        List<String> positionKeys = roleRepository.queryByUsernameAndType(u.getUsername(), ROLE_TYPE_POSITION)
                .stream().map(r -> r.getKey()).toList();
        List<String> roleKeys = roleRepository.queryByUsernameAndType(u.getUsername(), ROLE_TYPE_ROLE)
                .stream().map(r -> r.getKey()).toList();
        List<String> subRoleKeys = subRoleRepository.queryByUserId(u.getId()).stream()
                .map(s -> s.getKey()).filter(Objects::nonNull).toList();
        return new Row(u.getUsername(), u.getNickname(),
                positionKeys.isEmpty() ? null : positionKeys.get(0),
                roleKeys, subRoleKeys,
                u.getDesc(), u.getStatus(), u.getPassword());
    }
}
