package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.RelationRoleDTO;
import com.csm.paas.core.api.dto.SoftwareItemDTO;
import com.csm.paas.core.api.dto.SoftwareListDTO;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareListEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareMessageEntity;

/** 软件领域实体 → 厂商契约 DTO（`GET /v1/software`）。 */
public final class SoftwareListAssembler {

    private SoftwareListAssembler() {
    }

    public static SoftwareListDTO toResponse(SoftwareListEntity entity) {
        return SoftwareListDTO.builder()
                .softwareList(entity.getSoftwareList().stream().map(SoftwareListAssembler::toItem).toList())
                .total(entity.getTotal())
                .build();
    }

    /**
     * 软件实体 → **23 键**的 `Software` DTO（`POST /v1/software` 等端点的响应形状，
     * 与列表项的 27 键不同——多出来的 4 个附加键只属于列表）。
     */
    public static com.csm.paas.core.api.dto.SoftwareDTO toResponse(SoftwareEntity s) {
        return com.csm.paas.core.api.dto.SoftwareDTO.builder()
                .id(s.getId()).key(s.getKey()).name(s.getName()).appName(s.getAppName())
                .routerKey(s.getRouterKey()).type(s.getType()).version(s.getVersion())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .framework(s.getFramework()).execName(s.getExecName()).location(s.getLocation())
                .remoteIp(s.getRemoteIp()).remoteName(s.getRemoteName()).remotePassword(s.getRemotePassword())
                .activeCreate(s.getActiveCreate()).activeExpire(s.getActiveExpire())
                .orderTop(s.getOrderTop()).orderTime(s.getOrderTime())
                .desc(s.getDesc()).activeCode(s.getActiveCode()).screenshot(s.getScreenshot()).icon(s.getIcon())
                .build();
    }

    /** 单条消息 → 契约项（列表用；4 个附加键由 `SoftwareMessage` 提供）。 */
    public static SoftwareItemDTO toItem(SoftwareMessageEntity m) {
        SoftwareEntity s = m.getSoftware();
        return SoftwareItemDTO.builder()
                .id(s.getId()).key(s.getKey()).name(s.getName()).appName(s.getAppName())
                .routerKey(s.getRouterKey()).type(s.getType()).version(s.getVersion())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .framework(s.getFramework()).execName(s.getExecName()).location(s.getLocation())
                .remoteIp(s.getRemoteIp()).remoteName(s.getRemoteName()).remotePassword(s.getRemotePassword())
                .activeCreate(s.getActiveCreate()).activeExpire(s.getActiveExpire())
                .orderTop(s.getOrderTop()).orderTime(s.getOrderTime())
                .desc(s.getDesc()).activeCode(s.getActiveCode()).screenshot(s.getScreenshot()).icon(s.getIcon())
                .releaseStatus(m.getReleaseStatus())
                .roleList(m.getRoleList() == null ? null : m.getRoleList().stream()
                        .map(r -> RelationRoleDTO.builder().id(r.getId()).name(r.getName()).key(r.getKey()).build())
                        .toList())
                .web(m.getWeb())
                .url(m.getUrl())
                .build();
    }
}
