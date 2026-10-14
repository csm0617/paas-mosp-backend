package com.csm.paas.core.domain.subrole.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子角色导入的一行（厂商 `SubRoleServiceImpl.importSubroleExcel:583-625` 的"读单元格"结果）。
 *
 * <p>列序：0 名称 / 1 唯一标识 / 2 关联用户(JSON) / 3 关联战位(JSON) / 4 描述 /
 * 5 英文名 / 6 法文名。
 *
 * <p>`null` 表示"单元格读不到"（厂商对 null cell 直接 NPE 后逐个 catch），与"读到空串"不同。
 * 两个列表列：空白 ⇒ null；非法 JSON ⇒ 也按 null（统一化处理，见 `findings/158`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportSubRoleRowVO {

    /** 1 基行号（厂商错误信息 `subrole.import.key.not.valid` 带的就是它）。 */
    private int rowIndex;
    private String name;
    private String key;
    /** cell2：**用户名**列表。 */
    private List<String> usernames;
    /** cell3：战位 **`key`** 列表。 */
    private List<String> roleKeys;
    private String desc;
    private String nameUs;
    private String nameFr;
}
