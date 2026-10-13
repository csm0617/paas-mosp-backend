package com.csm.paas.core.domain.subrole.adapter.repository;

import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleRoleVO;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleUserVO;

import java.util.List;

/** 子角色仓储（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface ISubRoleRepository {

    /** 全量按 created_at 倒序分页（厂商 `SubRoleServiceImpl.java:188-191` 的空关键字分支）。 */
    List<SubRoleEntity> queryPageOrderByCreatedAtDesc(int offset, int size);

    long count();

    /** 按名称模糊（厂商 `:194` 的 `LIKE` 分支；该分支**不分页**）。 */
    List<SubRoleEntity> queryByNameLike(String likeKeyword);

    /** 子角色关联的用户（厂商 `getRoleUser`，`SubRoleServiceImpl.java:206-217`）。 */
    List<SubRoleUserVO> queryUsersBySubRoleId(String subRoleId);

    /** 子角色关联的战位（厂商 `getRoleRole`，`SubRoleServiceImpl.java:219-230`）。 */
    List<SubRoleRoleVO> queryRolesBySubRoleId(String subRoleId);
}
