package com.csm.paas.core.cases.user;

import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;

/** 新增用户的用例（`POST /v1/user`）。 */
public interface IUserAddCase {

    /**
     * 新增用户：校验与派生在领域层（`IUserCommandService.prepareNewUser`），
     * **落库 + 三类关系写入在一个事务里**。
     *
     * <p>为什么必须原子：厂商的 `UserService.add` **没有** `@Transactional`，而它先插用户行
     * （`UserService.java:514`）再写关系（`:518-550`）；一旦中途抛异常（实测：请求缺 `roleListKey`
     * 时在 `:545` 抛 NPE），**库里会留下一个没有任何关系的半成品用户**（见 `docs/findings/30` §5）。
     * 我们**不复制这个缺陷**：整个用例放进一个事务，失败即回滚。
     *
     * <p>关系写入照抄厂商的顺序与语义：`userListKey`（角色，type=2）→ `roleListKey`（战位，type=1）→
     * `subRoleListId`（子角色 id）；按 key 找不到的战位/角色**跳过**（厂商 `continue`）；
     * 子角色是"先删该用户全部关联再插入"（厂商 `SubRoleService.userAddRole`）。
     */
    UserEntity addUser(UserFormVO form);
}
