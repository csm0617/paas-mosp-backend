package com.csm.paas.core.cases.software.impl;

import com.csm.paas.core.cases.software.ISoftwareWriteCase;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO;
import com.csm.paas.core.domain.software.service.ISoftwareCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** 新增软件的事务编排（规则见 {@link ISoftwareWriteCase}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareWriteCaseImpl implements ISoftwareWriteCase {

    private final ISoftwareCommandService softwareCommandService;
    private final ISoftwareRepository softwareRepository;
    private final IRoleRepository roleRepository;

    @Override
    @Transactional
    public SoftwareEntity addSoftware(SoftwareFormVO form) {
        SoftwareEntity software = softwareCommandService.prepareNewSoftware(form);
        softwareRepository.insert(software);

        if (!CollectionUtils.isEmpty(form.getRoleListKey())) {
            softwareRepository.relateRoles(software.getId(), roleRepository.queryRoleIdsByKeys(form.getRoleListKey()));
        }
        log.info("已新增软件 id={} key={} name={}", software.getId(), software.getKey(), software.getName());
        return software;
    }
}
