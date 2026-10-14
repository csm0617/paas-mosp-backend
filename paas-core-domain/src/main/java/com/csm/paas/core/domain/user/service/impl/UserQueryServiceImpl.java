package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.service.RolePortalSupport;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserDetailEntity;
import com.csm.paas.core.domain.user.model.entity.UserListByRoleEntity;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.entity.UserInfoEntity;
import com.csm.paas.core.domain.user.model.entity.UserInfoPageEntity;
import com.csm.paas.core.domain.user.service.IUserQueryService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户管理列表——**逐条照抄厂商** {@code UserService.getUserList}
 * （{@code recovered/gaop-be/.../service/UserService.java:1828-1867}）。
 *
 * <p>厂商的四条特殊行为（都是契约，不是可选实现细节）：
 * <ol>
 *   <li>关键字**加上两个 {@code %} 之后**长度 &gt; 38 就报错
 *       （{@code user.invalid.search.keyword.length}，文案说"0~36 个字符"，与 38 差 2 个 {@code %} 吻合）；</li>
 *   <li>**跳过 username = {@code root} 的用户**（厂商硬编码，不返回）；</li>
 *   <li>昵称为空白串时置 **null**（不是空串）；</li>
 *   <li>{@code roles} 取 {@code g_role.type=1} 的名字、**{@code belongUsers} 取 {@code g_role.type=2} 的名字**
 *       （厂商字段名错位，见 {@code docs/findings/23-k1-live-schema-and-unique-constraints.md}）；
 *       {@code subRoles} 只在非空时落值，空则保持 null（序列化成 {@code "subRoles":null}）。</li>
 * </ol>
 *
 * <p><b>未实现</b>：厂商对每个名字过一层 i18n 覆盖
 * （{@code i18nUtil.getAutoNameMessageByCurrent("role", key, name)}）。本环境无覆盖数据，
 * 两种实现输出一致；有覆盖时我们会与厂商不同——已记入 {@code docs/findings/24-error-contract.md} 的未定论清单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements IUserQueryService {

    /** 厂商 {@code UserService.java:1838} 硬编码跳过。 */
    private static final String ROOT_USERNAME = "root";
    /** 厂商把关键字包成 {@code %kw%} 之后再判长度上限 38。 */
    private static final int LIKE_KEYWORD_MAX_LENGTH = 38;
    /** 战位（`g_role.type` = 1）。 */
    private static final int ROLE_TYPE_POSITION = 1;
    /** 角色（`g_role.type` = 2）——厂商把它填进 `belongUsers`。 */
    private static final int ROLE_TYPE_ROLE = 2;
    private static final String ADMIN_ROLE_KEY = "admin";

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final ISubRoleRepository subRoleRepository;

    @Override
    public UserInfoPageEntity queryUserInfoPage(int page, int size, String keyword) {
        String likeKeyword = (keyword == null || keyword.isEmpty()) ? null : "%" + keyword + "%";
        if (likeKeyword != null && likeKeyword.length() > LIKE_KEYWORD_MAX_LENGTH) {
            throw new WrongArgumentException("user.invalid.search.keyword.length");
        }

        List<UserEntity> users = userRepository.queryPageByKeyword(likeKeyword, PageUtils.offset(page, size), size);
        List<UserInfoEntity> list = users.stream()
                .filter(u -> !ROOT_USERNAME.equals(u.getUsername()))
                .map(this::toInfo)
                .toList();

        long total = userRepository.countByKeyword(likeKeyword);
        log.debug("查询用户列表 page={} size={} keyword={} 命中 {} 行（total={}）", page, size, keyword, list.size(), total);
        return UserInfoPageEntity.builder().list(list).total(total).build();
    }

    private UserInfoEntity toInfo(UserEntity u) {
        List<String> subRoleNames = userRepository.querySubRoleNamesByUserId(u.getId());
        return UserInfoEntity.builder()
                .id(u.getId())
                .nickname(StringUtils.hasText(u.getNickname()) ? u.getNickname() : null)
                .username(u.getUsername())
                .status(u.getStatus())
                .updatedAt(u.getUpdatedAt())
                .createdAt(u.getCreatedAt())
                .desc(u.getDesc())
                .lastLoginRole(u.getLastLoginRole())
                .roles(userRepository.queryRoleNamesByUsernameAndType(u.getUsername(), ROLE_TYPE_POSITION))
                .subRoles(subRoleNames == null || subRoleNames.isEmpty() ? null : subRoleNames)
                .belongUsers(userRepository.queryRoleNamesByUsernameAndType(u.getUsername(), ROLE_TYPE_ROLE))
                .build();
    }

    /**
     * 用户详情（厂商 `UserService.getUserDetail`，`:1756-1778`）。
     * <p>厂商顺序：先填基本字段，再取 type=1 的 `roles`、type=2 的 `belongRole`、用户的 `subRoles`，
     * 最后 `admin` = `roles` 里是否有 key=`admin`（`hasAdminPermission`，`:851-861`）。
     * <p>`roles`/`belongRole` 走的是 `RoleService.listByRoleType` ⇒ **带软件列表与 admin/dev 伪门户**；
     * `subRoles` 走 `SubRoleService.listByUserId` ⇒ **不填** userList/roleList（JSON 里是 null）。
     */
    @Override
    public UserDetailEntity queryDetailById(String userId) {
        UserEntity user = userRepository.queryById(userId)
                .orElseThrow(() -> new WrongArgumentException("user.missing.user"));

        List<RoleEntity> roles = rolesByType(user.getUsername(), ROLE_TYPE_POSITION);
        List<RoleEntity> belongRole = rolesByType(user.getUsername(), ROLE_TYPE_ROLE);

        return UserDetailEntity.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .status(user.getStatus())
                .email(user.getEmail())
                .birth(user.getBirth())
                .phone(user.getPhone())
                .updatedAt(user.getUpdatedAt())
                .createdAt(user.getCreatedAt())
                .desc(user.getDesc())
                .avatar(user.getAvatar())
                .lastLoginRole(user.getLastLoginRole())
                .uuid(user.getUuid())
                .roles(roles)
                .subRoles(subRoleRepository.queryByUserId(userId))
                .admin(roles.stream().anyMatch(r -> ADMIN_ROLE_KEY.equals(r.getKey())))
                .belongRole(belongRole)
                .build();
    }

    @Override
    public UserListByRoleEntity queryUsersByRoleId(String roleId, int page, int size) {
        List<UserEntity> users = userRepository.queryPageByRoleId(roleId, PageUtils.offset(page, size), size);
        long total = userRepository.countByRoleId(roleId);
        List<UserEntity> list = users.stream().filter(u -> !ROOT_USERNAME.equals(u.getUsername())).toList();
        return UserListByRoleEntity.builder().userList(list).total(total).build();
    }

    private List<RoleEntity> rolesByType(String username, int type) {
        List<RoleEntity> roles = roleRepository.queryByUsernameAndType(username, type);
        roles.forEach(role -> {
            role.setSoftwareList(roleRepository.querySoftwareByRoleId(role.getId()));
            RolePortalSupport.applyPortalOverride(role);
            RolePortalSupport.applyFlags(role);
        });
        return roles;
    }

}
