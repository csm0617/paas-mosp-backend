package com.csm.paas.core.domain.subrole.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子角色导出/导入模板的一行（厂商 `generateExportExcel:504-512` 写的 7 列）：
 * `名称 / 唯一标识 / 关联用户(JSON 数组) / 关联战位(JSON 数组) / 描述 / 英文名 / 法文名`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleExportRowVO {

    private String name;
    private String key;
    private List<String> usernames;
    private List<String> roleKeys;
    private String desc;
    private String nameUs;
    private String nameFr;
}
