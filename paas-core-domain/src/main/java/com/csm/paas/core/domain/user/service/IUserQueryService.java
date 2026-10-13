package com.csm.paas.core.domain.user.service;

import com.csm.paas.core.domain.user.model.entity.UserInfoPageEntity;

/** 用户查询服务（切片 3a：用户管理列表）。 */
public interface IUserQueryService {

    /**
     * 用户管理列表（厂商 {@code GET /v1/user/info}）。
     *
     * @param page    已归一化的页码（见 {@code PageUtils}）
     * @param size    已归一化的每页条数
     * @param keyword 用户名关键字（调用方已去空格；可为 null）
     */
    UserInfoPageEntity queryUserInfoPage(int page, int size, String keyword);

    /**
     * 用户详情（厂商 `GET /v1/user/detailByUserId`）。
     * <p>不存在时抛 `user.missing.user`（厂商 `UserService.java:1753`——注意不是 `user.not.found`）。
     */
    com.csm.paas.core.domain.user.model.entity.UserDetailEntity queryDetailById(String userId);
}
