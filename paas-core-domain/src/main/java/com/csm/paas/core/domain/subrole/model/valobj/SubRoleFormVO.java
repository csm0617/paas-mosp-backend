package com.csm.paas.core.domain.subrole.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子角色新增/修改表单（厂商 `SubRoleUserForm` 与 `SubRoleUpdateReq` 的并集；
 * 修改时 `id` 必填、新增时 `id` 由服务端生成）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleFormVO {

    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private String desc;
    private List<String> userList;
}
