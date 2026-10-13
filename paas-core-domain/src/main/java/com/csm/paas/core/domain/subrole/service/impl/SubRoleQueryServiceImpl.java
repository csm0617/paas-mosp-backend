package com.csm.paas.core.domain.subrole.service.impl;

import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleListEntity;
import com.csm.paas.core.domain.subrole.service.ISubRoleQueryService;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 子角色列表——照抄厂商 `SubRoleServiceImpl.switchRoleList`（`SubRoleServiceImpl.java:185-204`）。
 *
 * <p>厂商的两条分支**语义不同**，必须照抄：
 * <ol>
 *   <li>关键字为空：`PageHelper.startPage` + 按 `created_at` 倒序，`total` 取**分页总数**；</li>
 *   <li>关键字非空：`name LIKE`，**没有 `startPage`**（厂商漏了），`total` 取 `list.size()`——
 *       即"搜到多少返回多少，不分页"。</li>
 * </ol>
 * 另外每条记录都会被补上两个关系集合（`userList` / `roleList`），**无关联时是空数组而不是 null**
 * （实测样本 `v1_sub_role_switchRole_nonempty.json`）；而**新增接口**返回的同名字段是 `null`——
 * 两个端点的形状不同，不能共用装配逻辑。
 *
 * <p><b>未实现</b>：`name` 的 i18n 覆盖（`getAutoNameMessageByCurrent("sub_role", key, name)`）。
 * 本环境 `g_lang` 无覆盖数据，两种实现输出一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubRoleQueryServiceImpl implements ISubRoleQueryService {

    private final ISubRoleRepository subRoleRepository;

    @Override
    public SubRoleListEntity querySubRoleList(int page, int size, String subRoleName) {
        List<SubRoleEntity> list;
        long total;
        if (!StringUtils.hasText(subRoleName)) {
            list = subRoleRepository.queryPageOrderByCreatedAtDesc(PageUtils.offset(page, size), size);
            total = subRoleRepository.count();
        } else {
            list = subRoleRepository.queryByNameLike("%" + subRoleName + "%");
            total = list.size();
        }
        list.forEach(this::fillRelations);
        log.debug("查询子角色列表 page={} size={} name={} 命中 {} 行（total={}）", page, size, subRoleName, list.size(), total);
        return SubRoleListEntity.builder().list(list).total(total).build();
    }

    private void fillRelations(SubRoleEntity entity) {
        entity.setUserList(subRoleRepository.queryUsersBySubRoleId(entity.getId()));
        entity.setRoleList(subRoleRepository.queryRolesBySubRoleId(entity.getId()));
    }
}
