package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
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

    private final IUserRepository userRepository;

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
}
