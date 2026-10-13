package com.csm.paas.core.domain.subrole.model.entity;

import com.csm.paas.core.domain.subrole.model.valobj.SubRoleRoleVO;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleUserVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子角色（表 `g_sub_role`）的领域形态，含两个关系集合。
 * <p>字段与顺序照抄厂商 `model/SubRole.java`；实测样本
 * `docs/findings/runtime-samples/v1_sub_role_switchRole_nonempty.json`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleEntity {

    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    /** `g_user_sub_role` → 用户；厂商列表接口里**恒为数组**（无关联时是 `[]`，不是 null）。 */
    private List<SubRoleUserVO> userList;
    /** `g_sub_role_role` → 战位；同上恒为数组。 */
    private List<SubRoleRoleVO> roleList;
}
