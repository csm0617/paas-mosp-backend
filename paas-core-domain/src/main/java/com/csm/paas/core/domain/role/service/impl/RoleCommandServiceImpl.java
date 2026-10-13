package com.csm.paas.core.domain.role.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.service.IRoleCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 战位删除的前置校验（规则见 {@link IRoleCommandService} 的文档）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleCommandServiceImpl implements IRoleCommandService {

    private static final String ADMIN_KEY = "admin";
    private static final String DEV_KEY = "dev";

    private final IRoleRepository roleRepository;

    @Override
    public List<RoleEntity> validateDeletable(List<String> ids) {
        List<RoleEntity> deletable = new ArrayList<>();
        if (ids == null) {
            return deletable;
        }
        for (String id : ids) {
            RoleEntity role = roleRepository.queryById(id)
                    .orElseThrow(() -> new WrongArgumentException("role.missing.storage"));
            String key = role.getKey();
            if (ADMIN_KEY.equals(key) || DEV_KEY.equals(key)) {
                log.debug("无法删除当前战位：{}", key);
                throw new WrongArgumentException("role.cannot.delete.storage");
            }
            deletable.add(role);
        }
        return deletable;
    }
}
