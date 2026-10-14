package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.entity.UserRolesEntity;
import com.csm.paas.core.domain.user.model.entity.UserRolesPageEntity;
import com.csm.paas.core.domain.user.service.IUserRolesQueryService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 规则见 {@link IUserRolesQueryService}。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRolesQueryService implements IUserRolesQueryService {

    /** 厂商：`"%" + keyword + "%"` 的长度上限（含两个 %）。 */
    private static final int LIKE_KEYWORD_MAX_LENGTH = 38;

    private final IUserRepository userRepository;
    private final IRoleQueryService roleQueryService;
    private final ISubRoleRepository subRoleRepository;

    @Override
    public UserRolesPageEntity queryUserRolesPage(int page, int size, String keyword) {
        String likeKeyword = (keyword == null || keyword.isEmpty()) ? null : "%" + keyword + "%";
        if (likeKeyword != null && likeKeyword.length() > LIKE_KEYWORD_MAX_LENGTH) {
            throw new WrongArgumentException("user.invalid.search.keyword.length");
        }
        // **不过滤 root**（厂商 list() 没有 root 条件；/v1/user/info 才有）
        List<UserEntity> users = userRepository.queryPageByKeyword(likeKeyword, PageUtils.offset(page, size), size);
        List<UserRolesEntity> list = users.stream().map(this::toUserRoles).toList();
        long total = userRepository.countByKeyword(likeKeyword);
        log.debug("user/list page={} size={} keyword={} 命中 {} 行（total={}）", page, size, keyword, list.size(), total);
        return UserRolesPageEntity.builder().list(list).total(total).build();
    }

    /** 厂商 `getUserRoles:1780-1803`：**没有** setNickname；`roles` 走角色装配；`subRoles` 空时为空列表。 */
    private UserRolesEntity toUserRoles(UserEntity u) {
        List<RoleEntity> roles = roleQueryService.queryRoleList(u.getUsername(), 1, Integer.MAX_VALUE).getList();
        List<SubRoleEntity> subRoles = subRoleRepository.queryByUserId(u.getId());
        return UserRolesEntity.builder()
                .id(u.getId())
                .username(u.getUsername())
                .status(u.getStatus())
                .email(u.getEmail())
                .birth(u.getBirth())
                .phone(u.getPhone())
                .updatedAt(u.getUpdatedAt())
                .createdAt(u.getCreatedAt())
                .desc(u.getDesc())
                .avatar(u.getAvatar())
                .lastLoginRole(u.getLastLoginRole())
                .uuid(u.getUuid())
                .roles(roles)
                .subRoles(subRoles == null ? List.of() : subRoles)
                .admin(roles != null && roles.stream().anyMatch(r -> "admin".equals(r.getKey())))
                .build();
    }
}
