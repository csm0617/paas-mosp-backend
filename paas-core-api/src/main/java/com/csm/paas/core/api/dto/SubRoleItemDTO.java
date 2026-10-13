package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子角色列表项（厂商 `SubRole` 在 `GET /v1/sub_role/switchRole` 里的形态，10 个键）。
 * <p>实测样本 `docs/findings/runtime-samples/v1_sub_role_switchRole_nonempty.json`：
 * 列表接口里 `userList`/`roleList` **恒为数组**（空关联是 `[]`），`nameFr`/`nameUs` 恒为 `null`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleItemDTO {

    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    private List<SubRoleUserDTO> userList;
    private List<SubRoleRoleDTO> roleList;
}
