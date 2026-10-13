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
        return softwareDao.selectRelationRoles(roleIds).stream()
                .map(row -> RelationRoleVO.builder()
                        .id(str(row.get("id"))).name(str(row.get("name"))).key(str(row.get("key")))
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

    private SoftwarePO toPO(SoftwareEntity s) {
        SoftwarePO po = new SoftwarePO();
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
