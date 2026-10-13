package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.DeleteUserOutcome;
import com.csm.paas.core.domain.user.model.valobj.UserCallerVO;
import com.csm.paas.core.domain.user.service.IUserCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 用户删除（规则见 {@link IUserCommandService} 的文档）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements IUserCommandService {

    /** 内置超级管理员的用户名——厂商多处硬编码（`UserController.java:325,377`）。 */
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_ROLE_KEY = "admin";

    private final IUserRepository userRepository;

    @Override
    public DeleteUserOutcome deleteUser(String userId, UserCallerVO caller) {
        // **实测对拉纠正**：厂商控制器在删之前先调 `UserService.getById`，而 `getById` 抛的是
        // `user.id.not.found`（"查无此id的用户"，`UserService.java:1365`）——不是 `UserService.getDeleteUser`
        // 里那句 `user.not.found`（"用户不存在"）。后者在控制器路径上**是死代码**（只有服务层内部调用才到得了）。
        // 证据：2026-10-14 对拉 `DELETE /v1/user/nope`，厂商 msg = "查无此id的用户"。
        UserEntity target = userRepository.queryById(userId)
                .orElseThrow(() -> new WrongArgumentException("user.id.not.found"));

        if (ADMIN_USERNAME.equals(caller.getUsername())) {
            // 分支 1：admin 用户删 admin 用户 —— 厂商在这里才抛（也就是"admin 删自己"）
            if (ADMIN_USERNAME.equals(target.getUsername())) {
                throw new WrongArgumentException("role.can.not.delete.admin.key");
            }
            deleteCascade(target);
            return DeleteUserOutcome.DELETED;
        }

        if (caller.isAdminRole()) {
            // 分支 2：有 admin 战位的人不能删"带 admin 战位"的目标
            List<String> targetRoleKeys = userRepository.queryRoleKeysByUsername(target.getUsername());
            if (targetRoleKeys != null && targetRoleKeys.contains(ADMIN_ROLE_KEY)) {
                throw new WrongArgumentException("role.cannot.delete.op.admin");
            }
            deleteCascade(target);
            return DeleteUserOutcome.DELETED;
        }

        // 分支 3：**厂商返回 200 但不删**（UserController.java:357 返回 null，控制器照样 ok(true)）
        log.info("调用者 {} 既非 admin 用户也无 admin 战位：按厂商语义**不执行删除**，返回 200", caller.getUsername());
        return DeleteUserOutcome.NOOP_NOT_PERMITTED;
    }

    private void deleteCascade(UserEntity target) {
        userRepository.deleteUserCascade(target.getId());
        log.info("已删除用户 id={} username={}", target.getId(), target.getUsername());
    }
}
