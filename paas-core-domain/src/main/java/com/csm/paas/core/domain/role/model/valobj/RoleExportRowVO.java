package com.csm.paas.core.domain.role.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 战位导出/备份的一行（厂商 `RoleService.generateExportExcel:784-830`，8 列）。
 *
 * <p>列序（与 `TABLE_HEADERS` 一致）：名称 / 唯一标识 / 关联应用(JSON) / 关联子角色(JSON) /
 * 描述 / 关联用户(JSON) / 英文名 / 法文名。
 *
 * <p>三个列表**空即 null**（厂商：只有非空才 `gson.toJson(...)`，写进单元格就是空单元格）。
 * `nameUs`/`nameFr` 来自 `g_setting` 的自动名（键 `i18n.role.<lang>.<key>`），**取不到就是 null**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleExportRowVO {

    private String name;
    private String key;
    /** 关联应用的 **`key`** 列表（不是 id，也不是 name）。 */
    private List<String> softwareKeys;
    /** 关联子角色的 **`key`** 列表。 */
    private List<String> subRoleKeys;
    private String desc;
    /** 关联用户的 **用户名** 列表。 */
    private List<String> usernames;
    private String nameUs;
    private String nameFr;
}
