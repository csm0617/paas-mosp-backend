package com.csm.paas.core.domain.software.service;

import com.csm.paas.core.domain.software.model.entity.SoftwareInfoEntity;

/**
 * 软件详情（厂商 `GET /v1/software/{id}` → `SoftwareService.detailSoftware`，`:1563-1611`）。
 *
 * <p>拼装顺序照抄厂商：基本字段 → `screenShotList`（`screenshot` 按 `,` 拆）→
 * `roleKeyList`/`roleNameList`（`g_role_software.app_id` 反查战位）→ `nameUs`/`nameFr`（i18n 兜底 name）→
 * `licence`（`geneSoftwareCode(机器码, key)` + `getSoftwareLicenceInfo`）→
 * `version`/`uploadStoreRecord`（按 `s.version` 找上传记录）→ 框架是 `BS` 时清空 version/uploadStoreRecord 并算服务列表，
 * 否则 `serviceList = null`。
 *
 * <p><b>未实现</b>：
 * <ol>
 *   <li>`licence` 只做「未激活」分支（`active_code` 为 null ⇒ `LicenceInfo.expired(softwareCode)`）；
 *       「已激活 + JWT 验签 + 机器码交集」属 K3，未落地 ⇒ 该分支在我们后端**当前不可达**（没人会写 `active_code`）。</li>
 *   <li>`framework == "BS"` 时的 `serviceList`（厂商 `getServiceBySoftware` 要 K8s 现场态与 `g_software_app` 映射）；
 *       我们回到厂商字段的**默认值**（空列表）。非 BS 时厂商也是 `serviceList = null`，那条是照抄的。</li>
 * </ol>
 */
public interface ISoftwareDetailService {

    /** @throws com.csm.paas.core.types.exception.WrongArgumentException `software.software.id.not.found` */
    SoftwareInfoEntity queryDetail(String softwareId);
}
