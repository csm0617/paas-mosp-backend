package com.csm.paas.core.domain.role.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleOnlineUserRepository;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.model.valobj.OnlineUserVO;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 战位查询（`/v1/role`、`/v1/role/list`、`/v1/role/switchRole`、`/v1/role/{id}`）。
 *
 * <p>厂商四处特有行为，全部照抄（证据：`RoleService.java:172-399`、`518-521`）：
 * <ol>
 *   <li>`admin`/`dev` 两个战位的 `softwareList` 被**硬编码覆盖**为单条伪条目
 *       （`appId`=战位 key、`appName`="运维管理员门户"/"开发管理员门户"、`id`=0），即使 `g_role_software` 为空；</li>
 *   <li>**四个端点的集合填充各不相同**（实测样本）：`/v1/role` 只填 `softwareList`；`/v1/role/list` 多填 `userList`；
 *       `/switchRole` 四个集合全填；`/{id}` 全为 null 但 `nameFr`/`nameUs` 兜底成 `name`；</li>
 *   <li>`onLineUserList` **不是数据库数据**，来自 Redis `login-list:<user>:<roleKey>` 键（见 {@link IRoleOnlineUserRepository}）；</li>
 *   <li>关键字非空的分支**不分页**（厂商漏了 `startPage`），`total = list.size()`。</li>
 * </ol>
 * <p>战位名会再过一层 i18n 覆盖（{@code getAutoNameMessageByCurrent("role", key, name)}）；本环境无覆盖数据，
 * 两种实现输出一致，故先用库里的 name（见 docs/findings/28-e2e-role-page.md 的未定论清单）。
 * <p>中文乱码（若有）来自**播种期的双重编码入库**，不是厂商缺陷：厂商与本服务读同一份库、返回同样的字节，
 * 详见 docs/findings/11-own-seed-double-encoding.md。
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
    private final IRoleOnlineUserRepository onlineUserRepository;
    private final ISubRoleRepository subRoleRepository;

    @Override
    public RoleListEntity queryRoleList() {
        return queryRoleList(null, 1, Integer.MAX_VALUE);
    }

    @Override
    public RoleListEntity queryRoleList(String username, int page, int size) {
        List<RoleEntity> roles;
        long total;
        if (!StringUtils.hasText(username)) {
            roles = roleRepository.queryPage(PageUtils.offset(page, size), size);
            total = roleRepository.count();
        } else {
            roles = roleRepository.queryByUsername(username);
            total = roles.size();
        }
        roles.forEach(role -> {
            role.setSoftwareList(roleRepository.querySoftwareByRoleId(role.getId()));
            applyPortalOverride(role);
            applyFlags(role);
        });
        log.info("查询战位列表 username={} 共 {} 个（total={}）", username, roles.size(), total);
        return RoleListEntity.builder().list(roles).total(total).build();
    }

    @Override
    public com.csm.paas.core.domain.role.model.entity.RoleSoftwareListEntity querySoftwareList(
            String roleId, int page, int size) {
        int offset = com.csm.paas.core.types.util.PageUtils.offset(page, size);
        var list = roleRepository.querySoftwarePage(roleId, offset, size);
        long total = roleRepository.countSoftwareByRoleId(roleId);
        log.info("查询战位 {} 的软件列表 page={} size={} 共 {} 条", roleId, page, size, total);
        return com.csm.paas.core.domain.role.model.entity.RoleSoftwareListEntity.builder()
                .list(list).total(total).build();
    }

    @Override
    public RoleListEntity queryAllRoleList(String roleName, int page, int size) {
        List<RoleEntity> roles;
        long total;
        if (!StringUtils.hasText(roleName)) {
            roles = roleRepository.queryPage(PageUtils.offset(page, size), size);
            total = roleRepository.count();
        } else {
            roles = roleRepository.queryByNameLike("%" + roleName + "%");
            total = roles.size();
        }
        roles.forEach(role -> {
            role.setSoftwareList(roleRepository.querySoftwareByRoleId(role.getId()));
            // 与 /v1/role 的唯一差别：这里多填 userList（厂商 RoleService.java:282）
            role.setUserList(roleRepository.queryUsersByRoleId(role.getId()));
            applyPortalOverride(role);
            applyFlags(role);
        });
        log.info("查询全部战位 roleName={} 共 {} 个（total={}）", roleName, roles.size(), total);
        return RoleListEntity.builder().list(roles).total(total).build();
    }

    @Override
    public RoleListEntity querySwitchRoleList(String roleName, int page, int size, int type) {
        List<RoleEntity> roles;
        long total;
        if (!StringUtils.hasText(roleName)) {
            roles = roleRepository.queryPageByType(type, PageUtils.offset(page, size), size);
            total = roleRepository.countByType(type);
        } else {
            roles = roleRepository.queryByTypeAndNameLike(type, "%" + roleName + "%");
            total = roles.size();
        }
        Map<String, List<String>> onlineByRoleKey = onlineUsersByRoleKey();
        roles.forEach(role -> {
            role.setSoftwareList(roleRepository.querySoftwareByRoleId(role.getId()));
            role.setUserList(roleRepository.queryUsersByRoleId(role.getId()));
            role.setSubRoleList(subRoleRepository.queryByIds(roleRepository.querySubRoleIdsByRoleId(role.getId())));
            // 注意：switchRole 的 onLineUserList 恒为数组（无人在线是 []），不是 null
            role.setOnLineUserList(onlineByRoleKey.getOrDefault(role.getKey(), List.of()));
            applyPortalOverride(role);
            applyFlags(role);
        });
        log.info("查询战位/角色列表 type={} roleName={} 共 {} 个（total={}）", type, roleName, roles.size(), total);
        return RoleListEntity.builder().list(roles).total(total).build();
    }

    @Override
    public Optional<RoleEntity> queryDetail(String id) {
        return roleRepository.queryById(id).map(role -> {
            // 厂商 /v1/role/{id} **不做** admin/dev 的伪门户覆盖（实测样本 softwareList 为 null），
            // 也不覆盖 name；只兜底 nameFr/nameUs。三个布尔是模型上的计算 getter，恒有值。
            applyFlags(role);
            if (!StringUtils.hasText(role.getNameUs())) {
                role.setNameUs(role.getName());
            }
            if (!StringUtils.hasText(role.getNameFr())) {
                role.setNameFr(role.getName());
            }
            return role;
        });
    }

    @Override
    public Optional<RoleEntity> queryByKey(String key) {
        return roleRepository.queryByKey(key);
    }

    @Override
    public boolean existsByName(String roleName) {
        return roleRepository.existsByName(roleName);
    }

    @Override
    public List<RoleSoftwareEntity> querySoftwareByRoleId(String roleId) {
        return roleRepository.querySoftwareByRoleId(roleId);
    }

    /** 按战位 key 归组在线用户（厂商 `RoleService.java:206-237`）。 */
    private Map<String, List<String>> onlineUsersByRoleKey() {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (OnlineUserVO online : onlineUserRepository.queryOnlineUsers()) {
            if (online.getRoleKey() == null || online.getUsername() == null) {
                continue;
            }
            grouped.computeIfAbsent(online.getRoleKey(), k -> new ArrayList<>()).add(online.getUsername());
        }
        return grouped;
    }

    /** admin/dev 的伪门户覆盖（厂商 `RoleService.java:238-256`）。 */
    private void applyPortalOverride(RoleEntity role) {
        RoleSoftwareEntity hardcoded = hardcodedPortal(role.getKey());
        if (hardcoded != null) {
            role.setSoftwareList(new ArrayList<>(List.of(hardcoded)));
        }
    }

    /** 三个由 key 推导的布尔（厂商 `Role.java:33-43` 的计算 getter）。 */
    private void applyFlags(RoleEntity role) {
        boolean isAdmin = ADMIN_KEY.equals(role.getKey());
        boolean isDev = DEV_KEY.equals(role.getKey());
        role.setAdmin(isAdmin);
        role.setDev(isDev);
        role.setAdminOrDev(isAdmin || isDev);
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
