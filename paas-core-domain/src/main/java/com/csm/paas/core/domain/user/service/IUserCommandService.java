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

    /**
     * **构造**（不落库）一个新用户实体：跑完厂商 `add` 里的全部校验与派生。
     * <p>校验与派生（照抄 `UserService.java:450-513`）：
     * 用户名去空格 → 非空 / 字符黑名单 / 长度 2~36；口令去空格 → 长度 6~36；email ≤36；
     * desc 空白→null、≤255；分配 `uuid`（0..254 中最小的未占用值，用户数 &gt;256 抛 `user.data.limit.exceeded`）；
     * 口令 BCrypt；`avatar` 缺省用厂商的 `icon/avatar` 内容。
     * <p><b>落库与关系写入不在这里</b>：由 Case 层在一个事务里完成（见 `IUserAddCase`）。
     */
    com.csm.paas.core.domain.user.model.entity.UserEntity prepareNewUser(
            com.csm.paas.core.domain.user.model.valobj.UserFormVO form);
}
