package com.csm.paas.core.domain.subrole.service.impl;

import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.service.ISubRoleCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 子角色写操作（规格见 `docs/findings/147`）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubRoleCommandServiceImpl implements ISubRoleCommandService {

    private final ISubRoleRepository subRoleRepository;

    @Override
    public boolean deleteSubRoles(List<String> subRoleIds) {
        if (subRoleIds == null) {
            return true;
        }
        for (String subRoleId : subRoleIds) {
            // 厂商：key 非空白时删 i18n 自动名（本环境无 i18n 覆盖机制，跳过）
            subRoleRepository.queryById(subRoleId)
                    .filter(role -> role.getKey() != null && !role.getKey().isBlank())
                    .ifPresent(role -> log.info("删除子角色 {} 的 i18n 自动名（本环境未实现 i18n 覆盖）", role.getKey()));
            subRoleRepository.deleteSubRoleRolesBySubRoleId(subRoleId);
            subRoleRepository.deleteById(subRoleId);
            subRoleRepository.deleteUserSubRolesBySubRoleId(subRoleId);
        }
        return true;
    }
}
