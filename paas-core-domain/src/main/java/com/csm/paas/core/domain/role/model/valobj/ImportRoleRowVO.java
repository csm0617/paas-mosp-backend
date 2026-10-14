package com.csm.paas.core.domain.role.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 战位导入的一行（厂商 `RoleService.importRoleExcel:852-1124` 的"读单元格"结果）。
 *
 * <p>列序：0 名称 / 1 唯一标识 / 2 关联应用(JSON) / 3 关联子角色(JSON) / 4 描述 /
 * 5 关联用户(JSON) / 6 英文名 / 7 法文名。
 *
 * <p><b>null 的语义是"这个单元格读不到"</b>（厂商 `ExcelUtils.formatCellType(cell, STRING)`
 * 对 null cell 直接 NPE，被逐个 catch）——与"读到空串"不是一回事：厂商对这两者的处理不同
 * （例：`nameUs` 读不到就不写自动名，读到空串则**写一个空值**）。
 *
 * <p>三个列表列在触发层就解析成 `List<String>`（domain 没有 JSON 依赖）：**空白 ⇒ null**；
 * 非法 JSON ⇒ 也按 null（厂商在三种列/两个分支上的行为不一致：有的 500、有的吞掉——
 * 我们统一成"当没有列表"，见 `findings/158` 的偏离记录）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportRoleRowVO {

    /** 1 基行号（厂商的错误信息 `role.import.role.key.not.valid` 带的就是它）。 */
    private int rowIndex;
    /** cell0，表头是"名称"。 */
    private String name;
    /** cell1，表头是"唯一标识"。 */
    private String key;
    private List<String> softwareKeys;
    private List<String> subRoleKeys;
    private String desc;
    private List<String> usernames;
    /** cell6，自动名（英文）。 */
    private String nameUs;
    /** cell7，自动名（法文）。 */
    private String nameFr;
}
