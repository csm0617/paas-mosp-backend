package com.csm.paas.core.domain.user.model.valobj;

/**
 * 删除用户的结果。
 * <p>厂商有一个**静默不删**的分支：调用者既不是 `admin` 用户、又没有 admin 战位时，
 * `UserController.getDeleteUser` 返回 null，控制器却照样返回 `Response.ok(true)`——
 * **请求 200 成功，目标用户纹丝不动**（`UserController.java:357`）。
 * 这个"假成功"必须照抄：改成 403 会让前端表现与厂商不同。
 */
public enum DeleteUserOutcome {

    /** 真的删了。 */
    DELETED,

    /** 无权限，但厂商返回 200 且**什么都不做**。 */
    NOOP_NOT_PERMITTED
}
