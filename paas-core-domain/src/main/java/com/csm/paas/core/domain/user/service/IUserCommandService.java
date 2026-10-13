package com.csm.paas.core.domain.user.service;

import com.csm.paas.core.domain.user.model.valobj.DeleteUserOutcome;
import com.csm.paas.core.domain.user.model.valobj.UserCallerVO;

/**
 * 用户写操作（切片 3a 写路径）。本轮实现删除；新增/修改在后续轮次补。
 *
 * <p><b>删除的权限矩阵照抄厂商</b>（`UserController.getDeleteUser`，`UserController.java:320-358`）：
 * <ol>
 *   <li>调用者 username == `admin`：目标 username == `admin` → 抛 `role.can.not.delete.admin.key`；否则删；</li>
 *   <li>否则若调用者有 admin 战位：目标的战位列表含 `admin` → 抛 `role.cannot.delete.op.admin`；否则删；</li>
 *   <li>否则（既不是 admin 用户、也没有 admin 战位）→ **返回 200 但什么都不做**（见 {@link DeleteUserOutcome}）。</li>
 * </ol>
 * 目标不存在 → `user.id.not.found`（"查无此id的用户"；厂商 `UserService.getById`，
 * `UserService.java:1365`——**不是** `user.not.found`，后者在控制器路径上是死代码，已实测对拉确认）。
 */
public interface IUserCommandService {

    /**
     * 删除一个用户。
     *
     * @param userId 目标用户 id
     * @param caller 调用者身份（username + 是否拥有 admin 战位）
     * @return {@link DeleteUserOutcome#DELETED} 或 {@link DeleteUserOutcome#NOOP_NOT_PERMITTED}
     */
    DeleteUserOutcome deleteUser(String userId, UserCallerVO caller);
}
