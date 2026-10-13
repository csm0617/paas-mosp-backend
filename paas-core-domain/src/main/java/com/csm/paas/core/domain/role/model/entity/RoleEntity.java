package com.csm.paas.core.domain.role.model.entity;

import com.csm.paas.core.domain.role.model.valobj.RoleUserVO;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 战位（厂商 UI 叫"门户/席位"，表注释写"战位表"）。 */
@Data
@Builder
public class RoleEntity {
    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private String desc;
    private Integer type;
    private Long updatedAt;
    private Long createdAt;
    private List<RoleSoftwareEntity> softwareList;
    /**
     * 该战位当前在线的用户名。
     * <p>**null 与空列表语义不同**：`/v1/role`、`/v1/role/{id}` 恒为 null；
     * `/v1/role/switchRole` 恒为数组（无人在线是 `[]`）——照抄厂商（实测样本）。
     */
    private List<String> onLineUserList;
    /** 关联用户；`/v1/role/list` 与 `/switchRole` 有值，`/v1/role` 与 `/{id}` 为 null。 */
    private List<RoleUserVO> userList;
    /** 关联子角色；只有 `/switchRole` 有值。 */
    private List<SubRoleEntity> subRoleList;
    /** 由 key 推导：admin/dev/adminOrDev（厂商语义）。 */
    private Boolean admin;
    private Boolean dev;
    private Boolean adminOrDev;
}
