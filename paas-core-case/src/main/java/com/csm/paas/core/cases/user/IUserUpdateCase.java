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
}
