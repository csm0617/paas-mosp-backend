package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareBackupRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareBackupPortVO;
import com.csm.paas.core.infrastructure.dao.ISoftwareBackupDao;
import com.csm.paas.core.infrastructure.dao.po.ServicePortPO;
import com.csm.paas.core.infrastructure.dao.po.SoftwarePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 软件整包备份/还原仓储实现（**只调 dao**）。 */
@Repository
@RequiredArgsConstructor
public class SoftwareBackupRepository implements ISoftwareBackupRepository {

    private final ISoftwareBackupDao softwareBackupDao;

    @Override
    public List<SoftwareEntity> queryByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return softwareBackupDao.selectByIds(ids).stream().map(SoftwareBackupRepository::toEntity).toList();
    }

    @Override
    public List<SoftwareEntity> queryAll() {
        return softwareBackupDao.selectAll().stream().map(SoftwareBackupRepository::toEntity).toList();
    }

    @Override
    public Optional<SoftwareEntity> queryByKey(String key) {
        return Optional.ofNullable(softwareBackupDao.selectByKey(key))
                .map(SoftwareBackupRepository::toEntity);
    }

    @Override
    public List<String> queryRoleKeysBySoftwareId(String softwareId) {
        return softwareBackupDao.selectRoleKeysBySoftwareId(softwareId);
    }

    @Override
    public List<SoftwareBackupPortVO> queryServicePorts(String softwareId) {
        return softwareBackupDao.selectServicePortsBySoftware(softwareId).stream()
                .map(SoftwareBackupRepository::toPortVO).toList();
    }

    @Override
    public Optional<SoftwareBackupPortVO> queryFirstWebEntry(String softwareId) {
        return Optional.ofNullable(softwareBackupDao.selectFirstWebEntry(softwareId))
                .map(SoftwareBackupRepository::toPortVO);
    }

    @Override
    public void insertSoftwareApp(String softwareId, String appName, long createdAt) {
        softwareBackupDao.insertSoftwareApp(UUID.randomUUID().toString(), softwareId, appName, createdAt);
    }

    @Override
    public void deleteSoftwareAppsByNames(String softwareId, List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        softwareBackupDao.deleteSoftwareAppsByNames(softwareId, names);
    }

    @Override
    public void upsertServicePort(String softwareId, String serviceName, int port, String pathName,
                                  Boolean webEntry, long updatedAt) {
        ServicePortPO existing = softwareBackupDao.selectServicePort(softwareId, serviceName, port);
        if (existing == null) {
            softwareBackupDao.insertServicePort(softwareId, serviceName, port, pathName,
                    webEntry != null && webEntry, updatedAt);
            return;
        }
        softwareBackupDao.updateServicePortPathAndTime(existing.getId(), pathName, updatedAt);
    }

    @Override
    public void updateSoftwareForImport(SoftwareEntity software) {
        softwareBackupDao.updateByPrimaryKeyForImport(toPO(software));
    }

    private static SoftwareEntity toEntity(SoftwarePO po) {
        return SoftwareEntity.builder()
                .id(po.getId()).key(po.getKey()).name(po.getName()).appName(po.getAppName())
                .routerKey(po.getRouterKey()).type(po.getType()).version(po.getVersion())
                .createdAt(po.getCreatedAt()).updatedAt(po.getUpdatedAt())
                .framework(po.getFramework()).execName(po.getExecName()).location(po.getLocation())
                .remoteIp(po.getRemoteIp()).remoteName(po.getRemoteName()).remotePassword(po.getRemotePassword())
                .activeCreate(po.getActiveCreate()).activeExpire(po.getActiveExpire())
                .orderTop(po.getOrderTop()).orderTime(po.getOrderTime()).desc(po.getDesc())
                .activeCode(po.getActiveCode()).screenshot(po.getScreenshot()).icon(po.getIcon())
                .build();
    }

    private static SoftwarePO toPO(SoftwareEntity e) {
        SoftwarePO po = new SoftwarePO();
        po.setId(e.getId());
        po.setKey(e.getKey());
        po.setName(e.getName());
        po.setAppName(e.getAppName());
        po.setRouterKey(e.getRouterKey());
        po.setType(e.getType());
        po.setVersion(e.getVersion());
        po.setCreatedAt(e.getCreatedAt());
        po.setUpdatedAt(e.getUpdatedAt());
        po.setFramework(e.getFramework());
        po.setExecName(e.getExecName());
        po.setLocation(e.getLocation());
        po.setRemoteIp(e.getRemoteIp());
        po.setRemoteName(e.getRemoteName());
        po.setRemotePassword(e.getRemotePassword());
        po.setActiveCreate(e.getActiveCreate());
        po.setActiveExpire(e.getActiveExpire());
        po.setOrderTop(e.getOrderTop());
        po.setOrderTime(e.getOrderTime());
        po.setDesc(e.getDesc());
        po.setActiveCode(e.getActiveCode());
        po.setScreenshot(e.getScreenshot());
        po.setIcon(e.getIcon());
        return po;
    }

    private static SoftwareBackupPortVO toPortVO(ServicePortPO po) {
        return SoftwareBackupPortVO.builder()
                .serviceName(po.getServiceName()).port(po.getPort()).pathName(po.getPathName())
                .webEntry(po.getWebEntry()).allow(po.getAllow())
                .build();
    }
}
