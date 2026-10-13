package com.csm.paas.core.domain.subrole.service;

import com.csm.paas.core.domain.subrole.model.entity.SubRoleListEntity;

/** 子角色查询服务（切片 3c：子角色列表）。 */
public interface ISubRoleQueryService {

    /**
     * 子角色列表（厂商 `GET /v1/sub_role/switchRole`）。
     *
     * @param page 已归一化页码
     * @param size 已归一化每页条数
     * @param subRoleName 子角色名关键字（调用方已去空格；可为 null）
     */
    SubRoleListEntity querySubRoleList(int page, int size, String subRoleName);
}
