package com.csm.paas.core.domain.role.service;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 战位实体的两处厂商特有加工——抽出来给多个查询服务共用（`/v1/role*` 与 `/v1/user/detailByUserId` 都要）。
 * <ol>
 *   <li>{@link #applyPortalOverride}：`admin`/`dev` 的 `softwareList` 被**硬编码**覆盖为单条伪条目
 *       （`appId`=战位 key、`appName`="运维管理员门户"/"开发管理员门户"、`id`=0），即使 `g_role_software` 为空；</li>
 *   <li>{@link #applyFlags}：`admin`/`dev`/`adminOrDev` 三个布尔由 key 推导
 *       （厂商是模型上的计算 getter，`Role.java:33-43`）。</li>
 * </ol>
 * 厂商出处：`RoleService.java:238-256`、`Role.java:33-43`。
 * <p><b>注意 `/v1/role/{id}` 与 `/v1/role/switchRole` 的差异</b>：前者**不做**伪门户覆盖（实测 `softwareList` 为 null），
 * 所以调用方要自己决定调哪个方法，不能一股脑全调。
 */
public final class RolePortalSupport {

    private static final String ADMIN_KEY = "admin";
    private static final String DEV_KEY = "dev";
    private static final String ADMIN_PORTAL_NAME = "运维管理员门户";
    private static final String DEV_PORTAL_NAME = "开发管理员门户";

    private RolePortalSupport() {
    }

    public static void applyPortalOverride(RoleEntity role) {
        RoleSoftwareEntity hardcoded = hardcodedPortal(role.getKey());
        if (hardcoded != null) {
            role.setSoftwareList(new ArrayList<>(List.of(hardcoded)));
        }
    }

    public static void applyFlags(RoleEntity role) {
        boolean isAdmin = ADMIN_KEY.equals(role.getKey());
        boolean isDev = DEV_KEY.equals(role.getKey());
        role.setAdmin(isAdmin);
        role.setDev(isDev);
        role.setAdminOrDev(isAdmin || isDev);
    }

    private static RoleSoftwareEntity hardcodedPortal(String key) {
        if (ADMIN_KEY.equals(key)) {
            return pseudoPortal(ADMIN_KEY, ADMIN_PORTAL_NAME);
        }
        if (DEV_KEY.equals(key)) {
            return pseudoPortal(DEV_KEY, DEV_PORTAL_NAME);
        }
        return null;
    }

    private static RoleSoftwareEntity pseudoPortal(String key, String appName) {
        return RoleSoftwareEntity.builder().id(0L).roleId(key).appId(key).appName(appName).build();
    }
}
