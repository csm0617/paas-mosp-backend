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
    private final com.csm.paas.core.domain.software.service.ISoftwareDetailService softwareDetailService;

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

    @Override
    @Transactional
    public void removeSoftware(String softwareId) {
        SoftwareEntity software = softwareCommandService.requireExisting(softwareId);

        // 厂商顺序：g_role_software → g_software_app → g_service_port → g_software
        softwareRepository.deleteRoleSoftwareBySoftwareId(softwareId);
        softwareRepository.deleteSoftwareAppBySoftwareId(softwareId);
        softwareRepository.deleteServicePortBySoftwareId(softwareId);
        softwareRepository.deleteById(softwareId);

        log.info("已删除软件 id={} key={}", softwareId, software.getKey());
        // 厂商在删掉 g_service_port 行时会 notificationGateway() 通知网关重载；我们尚未接管网关（findings/39）
    }

    @Override
    @Transactional
    public com.csm.paas.core.domain.software.model.entity.SoftwareInfoEntity editSoftware(
            String softwareId, SoftwareFormVO form) {
        SoftwareEntity updated = softwareCommandService.prepareUpdatedSoftware(softwareId, form);
        softwareRepository.update(updated);

        // 厂商返回"重新查出来的 SoftwareInfo"，但 nameUs/nameFr 取自**表单**（空则回退 name）
        var detail = softwareDetailService.queryDetail(softwareId);
        detail.setNameUs(org.springframework.util.StringUtils.hasText(form.getNameUs())
                ? form.getNameUs() : detail.getSoftware().getName());
        detail.setNameFr(org.springframework.util.StringUtils.hasText(form.getNameFr())
                ? form.getNameFr() : detail.getSoftware().getName());

        log.info("已修改软件 id={} key={}", softwareId, updated.getKey());
        // 未实现：CS 类型的 WebSocket 通知；BS 类型改 key 后的服务重关联（要 K8s 与 g_software_app 映射）
        return detail;
    }
}
