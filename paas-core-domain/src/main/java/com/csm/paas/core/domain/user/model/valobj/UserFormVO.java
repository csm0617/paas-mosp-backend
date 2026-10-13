package com.csm.paas.core.domain.user.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 新增/修改用户的表单（厂商 `UserForm`，`controller/form/user/UserForm.java`）。
 * <p>字段名与厂商一致；`birth` 是 `java.util.Date`（契约见 `UserEntity.birth` 的注释）。
 * <p>三个列表字段的含义（容易搞混，厂商自己也绕）：
 * <ul>
 *   <li>{@code roleListKey} —— **战位**的 key 列表（`g_role.type=1`）；</li>
 *   <li>{@code userListKey} —— **角色**的 key 列表（`g_role.type=2`）；</li>
 *   <li>{@code subRoleListId} —— 子角色 **id** 列表（不是 key）。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserFormVO {

    private String username;
    private String nickname;
    private String password;
    private Integer status;
    private String email;
    private Date birth;
    private String phone;
    private String desc;
    private String avatar;
    private List<String> roleListKey;
    private List<String> userListKey;
    private List<String> subRoleListId;
}
