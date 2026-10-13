package com.csm.paas.core.domain.software.model.entity;

import com.csm.paas.core.domain.software.model.valobj.UploadStoreRecordVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 软件详情（厂商 `SoftwareInfo`）。
 * <p><b>`licence` 只实现了"未激活"那一支</b>：`active_code` 为 null（或非 null 但未做 JWT 验签）时，
 * 厂商给 `LicenceInfo.expired(code)`；「已激活且验签通过」需要 K3 的密码学，尚未实现。
 * 注意：在我们后端里 `active_code` 只能由 K3 授权流程写入，而 K3 尚未落地 ⇒ **该分支当前不可达**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareInfoEntity {

    private SoftwareEntity software;
    private List<String> roleKeyList;
    private List<String> roleNameList;
    private String nameUs;
    private String nameFr;
    private List<String> screenShotList;
    /** 机器码 + Base64(key) 拼出的软件码（`geneSoftwareCode`）。 */
    private String licenceCode;

    /** 授权信息（厂商 `getSoftwareLicenceInfo` 的结果：未激活/校验失败时是 `LicenceInfo.expired(code)`）。 */
    private com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity licence;
    private String version;
    private UploadStoreRecordVO uploadStoreRecord;
}
