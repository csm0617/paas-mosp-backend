package com.csm.paas.core.domain.software.service;

import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO;

/**
 * 软件写操作（新增）的校验与构造——厂商把这些规则写在 controller + service 里
 * （`SoftwareController.java:169-182`、`SoftwareService.java:607-647`），这里收回领域层。
 *
 * <p>校验矩阵（逐条照抄）：
 * <ul>
 *   <li>key 等于保留字 `api` → `software.key.identifier.reserved.word`（带参数）；</li>
 *   <li>key 已存在 → `software.key.identifier.existed`；name 已存在 → `software.name.existed`；</li>
 *   <li>name 为空 → `software.name.must.not.null`；长度 ∉ [2,100] → `software.name.illegal`（带参数）；</li>
 *   <li>key 为空 → `software.key.must.not.null`；首位是数字 → `...illegal.not.number.start`；
 *       首位是 `-` → `...illegal.not.dash.start`；不匹配 `^[a-z][a-z0-9-]*$` → `...illegal.format`；
 *       长度 ∉ [2,36] → `...illegal.length`（四个都带参数）；</li>
 *   <li>desc &gt;255 → `software.desc.length.invalid`（空白时的落库值见下）。</li>
 * </ul>
 * <p><b>一处照抄的古怪</b>：厂商 `softwareDescLimit` 在 desc 空白时 `sf.setDesc(null)`，但它**返回原始 desc**，
 * 调用方紧接着又 `sf.setDesc(desc)` —— 于是空白 desc 最终落库是**空串 `""`**，那个 null 被覆盖了。
 */
public interface ISoftwareCommandService {

    /** 校验并构造（不落库）新软件实体。 */
    SoftwareEntity prepareNewSoftware(SoftwareFormVO form);
}
