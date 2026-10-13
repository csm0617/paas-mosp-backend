package com.csm.paas.core.domain.role.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 战位列表查询。
 * <p>两处厂商特有行为，照抄（证据：`RoleService.java:361-399`）：
 * <ol>
 *   <li>`admin` / `dev` 两个战位的软件列表被**硬编码**覆盖为单条伪条目
 *       （`appId` = 战位 key，`appName` = "运维管理员门户" / "开发管理员门户"，`id` = 0）——即使 g_role_software 为空；</li>
 *   <li>战位名会再过一层 i18n（{@code i18nUtil.getAutoNameMessageByCurrent("role", key, name)}）。原型未实现 i18n 覆盖层，先用库里的 name。</li>
 * </ol>
 * 数据来源是 MySQL，**返回的是库里正确的 UTF-8 中文**；厂商侧同字段是双重编码的乱码（见 docs/findings/11-vendor-charset-defect.md）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleQueryServiceImpl implements IRoleQueryService {

    private static final String ADMIN_KEY = "admin";
    private static final String DEV_KEY = "dev";
    private static final String ADMIN_PORTAL_NAME = "运维管理员门户";
    private static final String DEV_PORTAL_NAME = "开发管理员门户";

    private final IRoleRepository roleRepository;

    @Override
    public RoleListEntity queryRoleList() {
        List<RoleEntity> roles = roleRepository.queryOrderByCreatedAtDesc();
        for (RoleEntity role : roles) {
            List<RoleSoftwareEntity> software = new ArrayList<>(
                    roleRepository.querySoftwareByRoleId(role.getId()));
            RoleSoftwareEntity hardcoded = hardcodedPortal(role.getKey());
            if (hardcoded != null) {
                software = new ArrayList<>(List.of(hardcoded));
            }
            role.setSoftwareList(software);
            boolean isAdmin = ADMIN_KEY.equals(role.getKey());
            boolean isDev = DEV_KEY.equals(role.getKey());
            role.setAdmin(isAdmin);
            role.setDev(isDev);
            role.setAdminOrDev(isAdmin || isDev);
        }
        log.info("查询战位列表 共 {} 个", roles.size());
        return RoleListEntity.builder().list(roles).total(roleRepository.count()).build();
    }

    private RoleSoftwareEntity hardcodedPortal(String key) {
        if (ADMIN_KEY.equals(key)) {
            return pseudoPortal(ADMIN_KEY, ADMIN_PORTAL_NAME);
        }
        if (DEV_KEY.equals(key)) {
            return pseudoPortal(DEV_KEY, DEV_PORTAL_NAME);
        }
        return null;
    }

    private RoleSoftwareEntity pseudoPortal(String key, String appName) {
        return RoleSoftwareEntity.builder()
                .id(0L)
                .roleId(key)
                .appId(key)
                .appName(appName)
                .build();
    }
}
