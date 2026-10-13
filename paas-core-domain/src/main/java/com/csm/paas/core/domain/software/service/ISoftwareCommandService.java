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

    /**
     * 放行 / 禁止软件（厂商 `POST /v1/software/release` → `SoftwareService.releaseSoftware`，`:2887-2931`）。
     *
     * <p>逐条照抄这两个分支的**不对称**：
     * <ul>
     *   <li>`list` 为空 → 直接返回 **true**（什么都不做）；</li>
     *   <li>`type == 1`（放行）：逐个软件若存在就插一条白名单 `/<key>/**`，**全部处理完**后返回 true；</li>
     *   <li>`type == 0`（禁止）：逐个软件若存在就删该路径，**一旦删到就立刻 return true**、删不到就立刻 return false
     *       —— 也就是它**只看第一个存在的软件**；</li>
     *   <li>其它 `type` 值 → 两个分支都不进，直接 true。</li>
     * </ul>
     *
     * <p><b>未实现</b>：厂商最后会调 `notificationGateway()` —— 起一个线程去
     * `GET http://&lt;gateway-host&gt;:&lt;port&gt;/refresh` 通知网关重载规则（fire-and-forget，成败不影响响应）。
     * 网关切流还没做（ADR-0005），该副作用记入待办。
     */
    boolean releaseSoftware(Integer type, java.util.List<String> softwareIds);

    /**
     * 校验待删软件存在（厂商 `SoftwareController.removeSoftWare` 先 `existSoftware(id)`，
     * 不存在 → `software.not.exist.by.id`），返回该实体供调用方继续删除。
     */
    SoftwareEntity requireExisting(String softwareId);

    /**
     * 校验并构造（不落库）修改后的软件实体（厂商 `SoftwareController.editSoftWare:365-378` +
     * `SoftwareService.editSoftWare:1482-1514`）。
     *
     * <p>校验顺序照抄：软件不存在 → `software.not.exist.by.id`；key 与他人重复 → `software.key.identifier.existed`；
     * name 与他人重复 → **同一个键** `software.key.identifier.existed`（厂商两处用了同一个 key，照抄）；
     * 然后 name/key 走与新增相同的规则（`software.name.*` / `software.key.identifier.illegal.*`）。
     *
     * <p>三处必须照抄的古怪：
     * <ol>
     *   <li>厂商用 `BeanUtils.copyProperties(form, sf)` ⇒ **表单里没带的字段会被置成 null**
     *       （包括 `routerKey`）——不是"没带就不改"；</li>
     *   <li>`desc` 空白时置 **null**（新增时那个 null 被返回值覆盖成空串，**修改时不会**，两处恰好相反）；</li>
     *   <li>整行更新（`updateByPrimaryKey`），不是 selective。</li>
     * </ol>
     * <p><b>未实现</b>：CS 类型的 WebSocket 通知、BS 类型改 key 后的服务重关联（`unRelateServices`/`relateServices`
     * 要 K8s 与 `g_software_app` 映射）。
     */
    SoftwareEntity prepareUpdatedSoftware(String softwareId, SoftwareFormVO form);
}
