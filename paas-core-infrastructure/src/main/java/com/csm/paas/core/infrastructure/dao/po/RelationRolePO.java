package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/**
 * 软件关联战位的投影（厂商 `RelationRole`：`{id,name,key}`）。
 * <p><b>必须用 PO 而不是 Map</b>：H2 返回的列标签是大写（`ID`/`NAME`/`KEY`），Map 的键会跟着变大写，
 * 而 MySQL 是小写——用 PO 让 MyBatis 做大小写不敏感的属性映射，两种库都能跑。
 * <p>（这个坑在同一轮里踩了第二次：`selectIdAndKeyByKeys` 那次也是它。）
 */
@Data
public class RelationRolePO {

    private String id;
    private String name;
    private String key;
}
