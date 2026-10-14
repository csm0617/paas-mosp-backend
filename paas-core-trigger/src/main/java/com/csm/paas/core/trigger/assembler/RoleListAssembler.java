package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.RoleItemDTO;
import com.csm.paas.core.api.dto.RoleListResponseDTO;
import com.csm.paas.core.api.dto.RoleSoftwareDTO;
import com.csm.paas.core.api.dto.RoleUserDTO;
import com.csm.paas.core.api.dto.RoleUsersDTO;
import com.csm.paas.core.api.dto.SubRoleItemDTO;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.model.entity.RoleUsersEntity;
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

    /**
     * 战位实体 → 契约项；登录响应内嵌的 role 用的是**同一形状**（实测 16 键）。
     * <p>`admin`/`dev`/`adminOrDev` **在这里按 key 现算**，不信任实体里存的值：厂商那三个是
     * **模型上的计算 getter**（`Role.java:33-43`），所以任何端点的响应里它们**永远不是 null**。
     * 新建/修改战位的响应走的就是"重新查出来的行"，如果只靠领域层设置就会出现 null
     * （2026-10-14 对拉实测到的差异：厂商 `false`、我们 `null`）。
     */
    public static RoleItemDTO toItemDTO(RoleEntity role) {
        boolean isAdmin = "admin".equals(role.getKey());
        boolean isDev = "dev".equals(role.getKey());
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
                .admin(isAdmin)
                .dev(isDev)
                .adminOrDev(isAdmin || isDev)
                .build();
    }

    /**
     * `RoleUsers` 实体 → DTO（`POST /v1/role/{roleId}/users`、`DELETE /v1/role/{roleId}/{userId}`）。
     * <p>四个集合键**一律 null**：厂商 `BeanUtils.copyProperties(role, roleUsers)` 只搬标量。
     * <p>`list` 里的 `password` 走 {@link UserAssembler#toResponse} 统一置 null（既有 L3 偏离账）。
     */
    public static RoleUsersDTO toRoleUsersDTO(RoleUsersEntity entity) {
        RoleEntity role = entity.getRole();
        boolean isAdmin = role != null && "admin".equals(role.getKey());
        boolean isDev = role != null && "dev".equals(role.getKey());
        return RoleUsersDTO.builder()
                .id(role == null ? null : role.getId())
                .key(role == null ? null : role.getKey())
                .name(role == null ? null : role.getName())
                .nameFr(role == null ? null : role.getNameFr())
                .nameUs(role == null ? null : role.getNameUs())
                .updatedAt(role == null ? null : role.getUpdatedAt())
                .createdAt(role == null ? null : role.getCreatedAt())
                .desc(role == null ? null : role.getDesc())
                .type(role == null ? null : role.getType())
                .onLineUserList(null)
                .softwareList(null)
                .userList(null)
                .subRoleList(null)
                .admin(isAdmin)
                .dev(isDev)
                .adminOrDev(isAdmin || isDev)
                .list(entity.getList() == null ? null
                        : entity.getList().stream().map(UserAssembler::toResponse).toList())
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
