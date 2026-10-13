package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.RoleItemDTO;
import com.csm.paas.core.api.dto.RoleListResponseDTO;
import com.csm.paas.core.api.dto.RoleSoftwareDTO;
import com.csm.paas.core.api.dto.RoleUserDTO;
import com.csm.paas.core.api.dto.SubRoleItemDTO;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleUserVO;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;

import java.util.List;

/**
 * 战位领域实体 → 厂商契约 DTO（`/v1/role`、`/v1/role/list`、`/v1/role/switchRole`、`/v1/role/{id}` 共用）。
 * <p><b>null 与空数组必须区分</b>：领域层没填的集合就是 null，填了但为空就是 `[]`——
 * 四个端点的差别全落在这个区分上（见 `docs/findings/28-e2e-role-page.md`）。
 */
public final class RoleListAssembler {

    private RoleListAssembler() {
    }

    public static RoleListResponseDTO toResponse(RoleListEntity entity) {
        return RoleListResponseDTO.builder()
                .list(entity.getList().stream().map(RoleListAssembler::toItemDTO).toList())
                .total(entity.getTotal())
                .build();
    }

    /** 战位实体 → 契约项；登录响应内嵌的 role 用的是**同一形状**（实测 16 键）。 */
    public static RoleItemDTO toItemDTO(RoleEntity role) {
        return RoleItemDTO.builder()
                .id(role.getId())
                .key(role.getKey())
                .name(role.getName())
                .nameFr(role.getNameFr())
                .nameUs(role.getNameUs())
                .updatedAt(role.getUpdatedAt())
                .createdAt(role.getCreatedAt())
                .desc(role.getDesc())
                .type(role.getType())
                .onLineUserList(role.getOnLineUserList())
                .softwareList(toSoftwareDTOs(role.getSoftwareList()))
                .userList(toUserDTOs(role.getUserList()))
                .subRoleList(toSubRoleDTOs(role.getSubRoleList()))
                .admin(role.getAdmin())
                .dev(role.getDev())
                .adminOrDev(role.getAdminOrDev())
                .build();
    }

    private static List<RoleUserDTO> toUserDTOs(List<RoleUserVO> list) {
        if (list == null) {
            return null;
        }
        return list.stream()
                .map(u -> RoleUserDTO.builder().userId(u.getUserId()).userName(u.getUserName()).build())
                .toList();
    }

    private static List<SubRoleItemDTO> toSubRoleDTOs(List<SubRoleEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(SubRoleListAssembler::toItemDTO).toList();
    }

    private static List<RoleSoftwareDTO> toSoftwareDTOs(List<RoleSoftwareEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(s -> RoleSoftwareDTO.builder()
                .id(s.getId())
                .roleId(s.getRoleId())
                .appId(s.getAppId())
                .appName(s.getAppName())
                .url(s.getUrl())
                .updatedAt(s.getUpdatedAt())
                .createdAt(s.getCreatedAt())
                .build()).toList();
    }
}
