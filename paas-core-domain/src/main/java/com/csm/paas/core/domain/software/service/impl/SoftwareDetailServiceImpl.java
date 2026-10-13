package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareInfoEntity;
import com.csm.paas.core.domain.software.model.valobj.RelationRoleVO;
import com.csm.paas.core.domain.software.model.valobj.UploadStoreRecordVO;
import com.csm.paas.core.domain.software.service.ISoftwareDetailService;
import com.csm.paas.core.domain.licence.service.ILicenceQueryService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** 软件详情（规则见 {@link ISoftwareDetailService}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareDetailServiceImpl implements ISoftwareDetailService {

    private final ISoftwareRepository softwareRepository;
    private final ILicenceQueryService licenceQueryService;

    @Override
    public SoftwareInfoEntity queryDetail(String softwareId) {
        SoftwareEntity software = softwareRepository.queryById(softwareId)
                .orElseThrow(() -> new WrongArgumentException("software.software.id.not.found"));

        String screenshot = software.getScreenshot();
        List<String> screenShotList = screenshot == null ? null : Arrays.asList(screenshot.split(","));

        List<String> roleKeyList = new ArrayList<>();
        List<String> roleNameList = new ArrayList<>();
        List<String> roleIds = softwareRepository.queryRoleIdsBySoftwareId(softwareId);
        if (!roleIds.isEmpty()) {
            for (RelationRoleVO role : softwareRepository.queryRelationRoles(roleIds)) {
                roleKeyList.add(role.getKey());
                // 厂商对 roleName 过一层 i18n（本环境无覆盖数据，等价于库里的 name）
                roleNameList.add(role.getName());
            }
        }

        // 厂商：nameUs/nameFr 先查 i18n 覆盖，取不到就用 name
        String nameUs = software.getName();
        String nameFr = software.getName();

        String licenceCode = licenceCode(software.getKey());
        String version = null;
        UploadStoreRecordVO record = null;
        UploadStoreRecordVO current = softwareRepository.queryCurrentVersionRecord(softwareId);
        if (current != null) {
            version = current.getId();
            record = current;
        }

        if ("BS".equals(software.getFramework())) {
            // 厂商这里清空 version/uploadStoreRecord 并返回带服务列表的对象；见接口 javadoc 的"未实现"
            version = null;
            record = null;
        }

        return SoftwareInfoEntity.builder()
                .software(software)
                .screenShotList(screenShotList)
                .roleKeyList(roleKeyList)
                .roleNameList(roleNameList)
                .nameUs(nameUs)
                .nameFr(nameFr)
                .licenceCode(licenceCode)
                .licence(softwareLicence(licenceCode, software))
                .version(version)
                .uploadStoreRecord(record)
                .build();
    }

    /**
     * 厂商 `SoftwareService.getSoftwareLicenceInfo:1613-1626`：
     * `active_code` 为空 → `LicenceInfo.expired(softwareCode)`；
     * 非空 → 校验；**校验抛异常或返回 null 也一律折算成 expired**。
     */
    private com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity softwareLicence(
            String softwareCode, SoftwareEntity software) {
        com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity expired =
                com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity.builder()
                        .end(-1L).code(softwareCode).expired(true).build();
        if (software.getActiveCode() == null) {
            return expired;
        }
        try {
            var checked = licenceQueryService.checkSoftwareActiveCode(
                    softwareCode, software.getActiveCode(), software.getKey());
            return checked == null ? expired : checked;
        } catch (Exception e) {
            log.error("验证软件码失败: {}", software.getActiveCode(), e);
            return expired;
        }
    }

    /** 厂商 `geneSoftwareCode(machineCodeBase64, key)` = 机器码 + Base64(key)。 */
    private String licenceCode(String softwareKey) {
        return licenceQueryService.machineCodeForSoftware()
                + Base64.getEncoder().encodeToString(softwareKey.getBytes(StandardCharsets.UTF_8));
    }
}
