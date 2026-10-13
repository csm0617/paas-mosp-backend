package com.csm.paas.core.cases.software;

import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO;

/** 新增软件的用例（`POST /v1/software`）。 */
public interface ISoftwareWriteCase {

    /**
     * 新增软件：校验与派生在领域层（`ISoftwareCommandService.prepareNewSoftware`），
     * **插行 + 战位关联写入在一个事务里**。
     *
     * <p>厂商 `addSoftWare`（`SoftwareService.java:607-647`）没有 `@Transactional`
     * （controller 上有，所以整体仍是事务）；关系部分：`roleListKey` 是**战位 key 列表**，
     * 逐个反查 id、找不到跳过，然后 `softwareRelateRole`（**已存在的关联跳过**，`order` = 该战位已有软件数 + 1）。
     * <p>`roleListKey` 为 null 时厂商会 NPE（缺陷，它只判了 `.size()`）；我们按空列表处理。
     */
    SoftwareEntity addSoftware(SoftwareFormVO form);
}
