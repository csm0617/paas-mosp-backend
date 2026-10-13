package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.SoftwareLicenceDTO;
import com.csm.paas.core.api.dto.SoftwareInfoDTO;
import com.csm.paas.core.api.dto.UploadStoreRecordDTO;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareInfoEntity;

/** 软件详情领域实体 → 厂商 `SoftwareInfo` DTO（23 键）。 */
public final class SoftwareInfoAssembler {

    private SoftwareInfoAssembler() {
    }

    public static SoftwareInfoDTO toResponse(SoftwareInfoEntity e) {
        SoftwareEntity s = e.getSoftware();
        return SoftwareInfoDTO.builder()
                .id(s.getId())
                .key(s.getKey())
                .name(s.getName())
                .nameFr(e.getNameFr())
                .nameUs(e.getNameUs())
                .type(s.getType())
                .desc(s.getDesc())
                .routerKey(s.getRouterKey())
                .location(s.getLocation())
                .remoteIp(s.getRemoteIp())
                .remoteName(s.getRemoteName())
                .remotePassword(s.getRemotePassword())
                .roleNameList(e.getRoleNameList())
                .roleKeyList(e.getRoleKeyList())
                .framework(s.getFramework())
                .execName(s.getExecName())
                .version(e.getVersion())
                .uploadStoreRecord(e.getUploadStoreRecord() == null ? null : UploadStoreRecordDTO.builder()
                        .id(e.getUploadStoreRecord().getId())
                        .realStoreFilename(e.getUploadStoreRecord().getRealStoreFilename())
                        .execFileName(e.getUploadStoreRecord().getExecFileName())
                        .updatedAt(e.getUploadStoreRecord().getUpdatedAt())
                        .execFaultMessage(e.getUploadStoreRecord().getExecFaultMessage())
                        .status(e.getUploadStoreRecord().getStatus())
                        .relationSoftwareId(e.getUploadStoreRecord().getRelationSoftwareId())
                        .build())
                // 厂商 SoftwareInfo 的字段初始化器把 status 默认设成 "STOP"（不是 null）
                .status("STOP")
                .serviceList(null)
                // 未激活/验签失败时厂商给 LicenceInfo.expired(code)：{end:-1, endIso8601:null, code, expired:true}
                .licence(SoftwareLicenceDTO.builder()
                        .end(-1L)
                        .endIso8601(null)
                        .code(e.getLicenceCode())
                        .expired(true)
                        .build())
                .icon(s.getIcon())
                .screenShotList(e.getScreenShotList())
                .build();
    }
}
