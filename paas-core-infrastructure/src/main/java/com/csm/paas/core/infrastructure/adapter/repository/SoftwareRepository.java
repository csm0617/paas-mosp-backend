package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.RelationRoleVO;
import com.csm.paas.core.infrastructure.dao.ISoftwareDao;
import com.csm.paas.core.infrastructure.dao.po.SoftwarePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SoftwareRepository implements ISoftwareRepository {

    private final ISoftwareDao softwareDao;

    @Override
    public List<SoftwareEntity> queryPage(String likeName, String type, String order, boolean asc,
                                          int offset, int size) {
        return softwareDao.selectPage(likeName, type, order, asc, offset, size).stream().map(this::toEntity).toList();
    }

    @Override
    public long count(String likeName, String type) {
        return softwareDao.selectCount(likeName, type);
    }

    @Override
    public boolean existsWhiteListPath(String path) {
        return softwareDao.countWhiteListPath(path) > 0;
    }

    @Override
    public List<RelationRoleVO> queryRelationRoles(List<String> roleIds) {
        // 用 PO 而不是 Map：H2 的列标签是大写，Map 取键会拿到 null（同 selectIdAndKeyByKeys 的坑）
        return softwareDao.selectRelationRoles(roleIds).stream()
                .map(row -> RelationRoleVO.builder()
                        .id(row.getId()).name(row.getName()).key(row.getKey())
                        .build())
                .toList();
    }

    @Override
    public List<String> queryRoleIdsBySoftwareId(String softwareId) {
        return softwareDao.selectRoleIdsBySoftwareId(softwareId);
    }

    @Override
    public List<String> queryWebEntryServiceNames(String softwareId) {
        return softwareDao.selectWebEntryServiceNames(softwareId);
    }

    @Override
    public boolean existsKey(String key) {
        return softwareDao.countByKey(key) > 0;
    }

    @Override
    public boolean existsName(String name) {
        return softwareDao.countByName(name) > 0;
    }

    @Override
    public void insert(SoftwareEntity software) {
        softwareDao.insert(toPO(software));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void relateRoles(String softwareId, java.util.List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;   // 厂商在空列表时直接 return（不清理旧关联）
        }
        long now = System.currentTimeMillis();
        for (String roleId : new java.util.LinkedHashSet<>(roleIds)) {
            if (softwareDao.countRelation(roleId, softwareId) > 0) {
                continue;   // 已存在的关联跳过
            }
            int order = (int) softwareDao.countRelationByRoleId(roleId) + 1;
            softwareDao.insertRelation(roleId, softwareId, softwareId, order, now);
        }
    }

    @Override
    public void deleteRoleSoftwareBySoftwareId(String softwareId) {
        softwareDao.deleteRoleSoftwareBySoftwareId(softwareId);
    }

    @Override
    public void deleteRoleSoftwareRelation(String roleId, String softwareId) {
        softwareDao.deleteRoleSoftwareByRoleIdAndSoftwareId(roleId, softwareId);
    }

    @Override
    public int deleteSoftwareAppBySoftwareId(String softwareId) {
        return softwareDao.deleteSoftwareAppBySoftwareId(softwareId);
    }

    @Override
    public int deleteServicePortBySoftwareId(String softwareId) {
        return softwareDao.deleteServicePortBySoftwareId(softwareId);
    }

    @Override
    public void deleteById(String softwareId) {
        softwareDao.deleteById(softwareId);
    }

    @Override
    public java.util.Optional<SoftwareEntity> queryById(String softwareId) {
        SoftwarePO po = softwareDao.selectById(softwareId);
        return po == null ? java.util.Optional.empty() : java.util.Optional.of(toEntity(po));
    }

    @Override
    public java.util.Optional<String> queryIdByKey(String key) {
        return java.util.Optional.ofNullable(softwareDao.selectIdByKey(key));
    }

    @Override
    public boolean existsById(String softwareId) {
        return softwareDao.countById(softwareId) > 0;
    }

    @Override
    public boolean existsByKeyExcludingId(String key, String id) {
        return softwareDao.countByKeyExcludingId(key, id) > 0;
    }

    @Override
    public boolean existsByNameExcludingId(String name, String id) {
        return softwareDao.countByNameExcludingId(name, id) > 0;
    }

    @Override
    public void update(SoftwareEntity software) {
        softwareDao.updateByPrimaryKey(toPO(software));
    }

    @Override
    public void insertWhiteListPath(String path, long createdAt) {
        softwareDao.insertWhiteListPath(path, createdAt);
    }

    @Override
    public int deleteWhiteListPath(String path) {
        return softwareDao.deleteWhiteListPath(path);
    }

    @Override
    public com.csm.paas.core.domain.software.model.valobj.UploadStoreRecordVO queryCurrentVersionRecord(String softwareId) {
        com.csm.paas.core.infrastructure.dao.po.UploadStoreRecordPO po = softwareDao.selectCurrentVersionRecord(softwareId);
        if (po == null) {
            return null;
        }
        return com.csm.paas.core.domain.software.model.valobj.UploadStoreRecordVO.builder()
                .id(po.getId()).realStoreFilename(po.getRealStoreFilename()).execFileName(po.getExecFileName())
                .updatedAt(po.getUpdatedAt()).execFaultMessage(po.getExecFaultMessage())
                .status(po.getStatus()).relationSoftwareId(po.getRelationSoftwareId())
                .build();
    }

    @Override
    public List<SoftwareEntity> queryAuthPage(String likeName, String type, int offset, int size) {
        return softwareDao.selectAuthPage(likeName, type, offset, size).stream().map(this::toEntity).toList();
    }

    @Override
    public long countAuth(String likeName, String type) {
        return softwareDao.selectAuthCount(likeName, type);
    }

    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.ServicePortVO> queryServicePorts(
            String softwareId, String serviceName) {
        return softwareDao.selectServicePorts(softwareId, serviceName).stream()
                .map(po -> com.csm.paas.core.domain.software.model.valobj.ServicePortVO.builder()
                        .id(po.getId()).softwareId(po.getSoftwareId()).serviceName(po.getServiceName())
                        .port(po.getPort()).pathName(po.getPathName()).webEntry(po.getWebEntry())
                        .allow(po.getAllow()).updatedAt(po.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    public boolean existsServicePath(String softwareId, String serviceName, String pathName) {
        return softwareDao.countServicePath(softwareId, serviceName, pathName) > 0;
    }

    @Override
    public com.csm.paas.core.domain.software.model.valobj.ServicePortVO queryServicePort(
            String softwareId, String serviceName, int port) {
        return toPortVO(softwareDao.selectServicePort(softwareId, serviceName, port));
    }

    @Override
    public com.csm.paas.core.domain.software.model.valobj.ServicePortVO updateServicePortPath(long id, String pathName) {
        softwareDao.updateServicePortPath(id, pathName);
        return toPortVO(softwareDao.selectServicePortById(id));   // 厂商：更新后再查一次并返回
    }

    private static com.csm.paas.core.domain.software.model.valobj.ServicePortVO toPortVO(
            com.csm.paas.core.infrastructure.dao.po.ServicePortPO po) {
        if (po == null) {
            return null;
        }
        return com.csm.paas.core.domain.software.model.valobj.ServicePortVO.builder()
                .id(po.getId()).softwareId(po.getSoftwareId()).serviceName(po.getServiceName())
                .port(po.getPort()).pathName(po.getPathName()).webEntry(po.getWebEntry())
                .allow(po.getAllow()).updatedAt(po.getUpdatedAt())
                .build();
    }

    @Override
    public void clearSoftwareAuth(String softwareId, long updatedAt) {
        softwareDao.clearSoftwareAuth(softwareId, updatedAt);
    }

    @Override
    public void saveSoftwareAuth(String softwareId, long activeCreate, String activeCode, long activeExpire) {
        softwareDao.saveSoftwareAuth(softwareId, activeCreate, activeCode, activeExpire);
    }

    @Override
    public void updateActiveCode(String softwareId, String activeCode) {
        softwareDao.updateActiveCode(softwareId, activeCode);
    }

    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.PodRestartInfoVO> queryPodRestartInfo(String serviceName) {
        return softwareDao.selectPodEvents(serviceName).stream()
                .map(po -> com.csm.paas.core.domain.software.model.valobj.PodRestartInfoVO.builder()
                        .podName(po.getPodName()).restartCount(po.getRestartCount()).createTime(po.getCreateAt())
                        .build())
                .toList();
    }

    @Override
    public List<String> queryServicePathsBySoftware(String softwareId) {
        return softwareDao.selectServicePathsBySoftware(softwareId);
    }

    @Override
    public List<String> querySoftwareIdsByAppName(String appName) {
        return softwareDao.selectSoftwareIdsByAppName(appName);
    }

    @Override
    public List<String> queryAppNamesBySoftwareId(String softwareId) {
        return softwareDao.selectAppNamesBySoftwareId(softwareId);
    }

    @Override
    public void updateSoftwareUpdatedAt(String softwareId, long updatedAt) {
        softwareDao.updateSoftwareUpdatedAt(softwareId, updatedAt);
    }

    @Override
    public void updateSoftwareOrderTop(String softwareId, Integer orderTop, long orderTime) {
        softwareDao.updateSoftwareOrderTop(softwareId, orderTop, orderTime);
    }

    @Override
    public void updateSoftwareIcon(String softwareId, String icon, long updatedAt) {
        softwareDao.updateSoftwareIcon(softwareId, icon, updatedAt);
    }

    @Override
    public void updateSoftwareScreenshot(String softwareId, String screenshot, long updatedAt) {
        softwareDao.updateSoftwareScreenshot(softwareId, screenshot, updatedAt);
    }

    @Override
    public List<SoftwareEntity> queryAllSoftware() {
        return softwareDao.selectAllSoftware().stream().map(this::toEntity).toList();
    }

    @Override
    public List<Integer> queryPodEventRestartCounts(String serviceName, String podName) {
        return softwareDao.selectPodEventsByServiceAndPod(serviceName, podName).stream()
                .map(com.csm.paas.core.infrastructure.dao.po.PodEventPO::getRestartCount)
                .toList();
    }

    @Override
    public void insertPodEvent(String serviceName, String podName, long createAt, int restartCount) {
        softwareDao.insertPodEvent(serviceName, podName, createAt, restartCount);
    }

    @Override
    public java.util.Optional<Integer> queryWebEntryPort(String softwareId, String serviceName) {
        return java.util.Optional.ofNullable(softwareDao.selectWebEntryPort(softwareId, serviceName));
    }

    @Override
    public void insertServicePortRow(String softwareId, String serviceName, int port, String pathName,
                                     boolean webEntry, long updatedAt) {
        softwareDao.insertServicePortRow(softwareId, serviceName, port, pathName, webEntry, updatedAt);
    }

    @Override
    public void deleteServicePorts(String softwareId, String serviceName) {
        softwareDao.deleteServicePortsBySoftwareIdAndServiceName(softwareId, serviceName);
    }

    @Override
    public void deleteSoftwareAppsByNames(String softwareId, java.util.List<String> names) {
        if (names != null && !names.isEmpty()) {
            softwareDao.deleteSoftwareAppsByNames(softwareId, names);
        }
    }

    @Override
    public void deleteServicePortsByNames(String softwareId, java.util.List<String> names) {
        if (names != null && !names.isEmpty()) {
            softwareDao.deleteServicePortsByNames(softwareId, names);
        }
    }

    @Override
    public void insertServicePorts(List<com.csm.paas.core.domain.software.model.valobj.ServicePortVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;   // 空列表：厂商会拼出非法 SQL（由领域层显式抛 400），这里不制造坏 SQL
        }
        softwareDao.insertServicePorts(rows.stream().map(r -> {
            var po = new com.csm.paas.core.infrastructure.dao.po.ServicePortPO();
            po.setSoftwareId(r.getSoftwareId());
            po.setServiceName(r.getServiceName());
            po.setPort(r.getPort());
            po.setPathName(r.getPathName());
            po.setWebEntry(r.getWebEntry());
            po.setAllow(r.getAllow());
            po.setUpdatedAt(r.getUpdatedAt());
            return po;
        }).toList());
    }

    // ---------- 软件服务入口（厂商 `POST /v1/software/{id}/service_entry`）----------

    @Override
    public void updateWebEntry(String softwareId, String serviceName, int port, boolean webEntry) {
        softwareDao.updateWebEntry(softwareId, serviceName, port, webEntry);
    }

    @Override
    public void clearOtherWebEntries(String softwareId, String serviceName, int port) {
        softwareDao.clearOtherWebEntries(softwareId, serviceName, port);
    }

    @Override
    public void updateRouterKey(String softwareId, String routerKey, long updatedAt) {
        softwareDao.updateRouterKey(softwareId, routerKey, updatedAt);
    }

    private SoftwarePO toPO(SoftwareEntity s) {        SoftwarePO po = new SoftwarePO();
        po.setId(s.getId());
        po.setKey(s.getKey());
        po.setName(s.getName());
        po.setAppName(s.getAppName());
        po.setRouterKey(s.getRouterKey());
        po.setType(s.getType());
        po.setVersion(s.getVersion());
        po.setCreatedAt(s.getCreatedAt());
        po.setUpdatedAt(s.getUpdatedAt());
        po.setFramework(s.getFramework());
        po.setExecName(s.getExecName());
        po.setLocation(s.getLocation());
        po.setRemoteIp(s.getRemoteIp());
        po.setRemoteName(s.getRemoteName());
        po.setRemotePassword(s.getRemotePassword());
        po.setActiveCreate(s.getActiveCreate());
        po.setActiveExpire(s.getActiveExpire());
        po.setOrderTop(s.getOrderTop());
        po.setOrderTime(s.getOrderTime());
        po.setDesc(s.getDesc());
        po.setActiveCode(s.getActiveCode());
        po.setScreenshot(s.getScreenshot());
        po.setIcon(s.getIcon());
        return po;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    // ---------- CS 程序包上传/关联 ----------

    @Override
    public java.util.Optional<com.csm.paas.core.domain.software.model.valobj.UploadStoreRecordVO>
    queryUploadRecordById(String uploadStoreRecordId) {
        var po = softwareDao.selectUploadRecordById(uploadStoreRecordId);
        if (po == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(com.csm.paas.core.domain.software.model.valobj.UploadStoreRecordVO.builder()
                .id(po.getId()).realStoreFilename(po.getRealStoreFilename()).execFileName(po.getExecFileName())
                .updatedAt(po.getUpdatedAt()).execFaultMessage(po.getExecFaultMessage())
                .status(po.getStatus()).relationSoftwareId(po.getRelationSoftwareId())
                .build());
    }

    @Override
    public void insertUploadRecord(String id, String realStoreFilename, String execFileName, long updatedAt,
                                   int status, String execFaultMessage, String relationSoftwareId) {
        com.csm.paas.core.infrastructure.dao.po.UploadStoreRecordPO po =
                new com.csm.paas.core.infrastructure.dao.po.UploadStoreRecordPO();
        po.setId(id);
        po.setRealStoreFilename(realStoreFilename);
        po.setExecFileName(execFileName);
        po.setUpdatedAt(updatedAt);
        po.setStatus(status);
        po.setExecFaultMessage(execFaultMessage);
        po.setRelationSoftwareId(relationSoftwareId);
        softwareDao.insertUploadRecord(po);
    }

    @Override
    public java.util.List<String> queryRoleIdsBySoftwareIdDesc(String softwareId) {
        return softwareDao.selectRoleIdsBySoftwareIdDesc(softwareId);
    }

    private SoftwareEntity toEntity(SoftwarePO po) {
        return SoftwareEntity.builder()
                .id(po.getId()).key(po.getKey()).name(po.getName()).appName(po.getAppName())
                .routerKey(po.getRouterKey()).type(po.getType()).version(po.getVersion())
                .createdAt(po.getCreatedAt()).updatedAt(po.getUpdatedAt())
                .framework(po.getFramework()).execName(po.getExecName()).location(po.getLocation())
                .remoteIp(po.getRemoteIp()).remoteName(po.getRemoteName()).remotePassword(po.getRemotePassword())
                .activeCreate(po.getActiveCreate()).activeExpire(po.getActiveExpire())
                .orderTop(po.getOrderTop()).orderTime(po.getOrderTime())
                .desc(po.getDesc()).activeCode(po.getActiveCode()).screenshot(po.getScreenshot()).icon(po.getIcon())
                .build();
    }
}
