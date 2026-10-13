package com.csm.paas.core.cases.user;

import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;

/** 修改用户的用例（`PUT /v1/user/{userId}`）。 */
public interface IUserUpdateCase {

    /**
     * 修改用户：校验与"哪些字段能改"在领域层（`IUserCommandService.prepareUpdatedUser`），
     * **更新用户行 + 三类关系在一个事务里**。
     *
     * <p>关系语义照抄厂商 `UserService.update`（`:636-678`）——注意与"新增"不同，这里是**空即删除**：
     * <ul>
     *   <li>`userListKey` 为 null 或空 → **删除**该用户 type=2 的关联；非空 → 替换；</li>
     *   <li>`roleListKey` 为 null 或空 → **删除**该用户 type=1 的关联；非空 → 替换；</li>
     *   <li>`subRoleListId` 非空 → 替换；为空 → **删除**该用户全部子角色关联。</li>
     * </ul>
     */
    UserEntity updateUser(String userId, UserFormVO form);

    /**
     * 厂商 `POST /v1/user/userToRole`：**替换用户的全部战位/角色关联**（不分类型）。
     * <p>厂商 `UserService.UserRelationRole`（`:1869-1892`）：`roleIdList` 先过 `HashSet` **去重**
     * （所以顺序不确定），然后删掉该用户所有 `g_user_role` 再逐条插入；列表为空则只删不插。
     * <p>用户不存在 → `user.missing.user.info`（"找不到改用户信息，无法进行关联操作"）。
     *
     * @return 厂商恒返回 `true`
     */
    boolean replaceUserRoles(String userId, java.util.List<String> roleIdList);

    /**
     * 厂商 `POST /v1/user/{userId}/roles`：**只替换 `type=1`（战位）**的关联。
     * <p>厂商 `UserController.roleRelate2user`（`:432-440`）→ `rolesRelate2User(userId, 1, roleIdList)`；
     * 用户不存在 → `user.not.found`；按 id 找不到的战位**跳过**。
     */
    void relatePositions(String userId, java.util.List<String> roleIdList);
}
