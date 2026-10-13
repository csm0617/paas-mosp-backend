package com.csm.paas.core.cases.software;

import com.csm.paas.core.cases.software.impl.SoftwareWriteCaseImpl;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO;
import com.csm.paas.core.domain.software.service.ISoftwareCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 新增/删除软件的编排（离线）。
 * <p>删除顺序照抄厂商（`SoftwareService.removeSoftware:1520-1533`）：
 * `g_role_software` → `g_software_app` → `g_service_port` → `g_software`。
 */
class SoftwareWriteCaseImplTest {

    private final ISoftwareCommandService commandService = mock(ISoftwareCommandService.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final com.csm.paas.core.domain.software.service.ISoftwareDetailService detailService =
            mock(com.csm.paas.core.domain.software.service.ISoftwareDetailService.class);

    private SoftwareWriteCaseImpl useCase() {
        return new SoftwareWriteCaseImpl(commandService, softwareRepository, roleRepository, detailService);
    }

    /**
     * 修改软件：先按领域规则构造"修改后的实体"（含"表单没带的字段会被置 null"），
     * 再整行更新，最后以**重新查出来的详情**作为响应（`nameUs`/`nameFr` 取自表单）。
     */
    @Test
    void editUpdatesRowThenReturnsDetailWithFormNames() {
        SoftwareEntity updated = SoftwareEntity.builder().id("s1").key("myapp").name("新名字").build();
        when(commandService.prepareUpdatedSoftware(org.mockito.ArgumentMatchers.eq("s1"), any()))
                .thenReturn(updated);
        var detail = com.csm.paas.core.domain.software.model.entity.SoftwareInfoEntity.builder()
                .software(SoftwareEntity.builder().id("s1").key("myapp").name("新名字").build())
                .build();
        when(detailService.queryDetail("s1")).thenReturn(detail);

        var result = useCase().editSoftware("s1", SoftwareFormVO.builder()
                .key("myapp").name("新名字").nameUs("NewUs").build());

        verify(softwareRepository).update(updated);
        org.junit.jupiter.api.Assertions.assertEquals("NewUs", result.getNameUs(), "nameUs 取自表单");
        org.junit.jupiter.api.Assertions.assertEquals("新名字", result.getNameFr(), "nameFr 表单为空 → 回退 name");
    }

    @Test
    void addInsertsThenRelatesRolesByKey() {
        SoftwareEntity prepared = SoftwareEntity.builder().id("s1").key("myapp").build();
        when(commandService.prepareNewSoftware(any())).thenReturn(prepared);
        when(roleRepository.queryRoleIdsByKeys(List.of("admin"))).thenReturn(List.of("r1"));

        useCase().addSoftware(SoftwareFormVO.builder().key("myapp").name("名字").roleListKey(List.of("admin")).build());

        InOrder order = inOrder(softwareRepository);
        order.verify(softwareRepository).insert(prepared);
        order.verify(softwareRepository).relateRoles("s1", List.of("r1"));
    }

    @Test
    void addWithoutRoleKeysDoesNotTouchRelations() {
        when(commandService.prepareNewSoftware(any()))
                .thenReturn(SoftwareEntity.builder().id("s1").key("myapp").build());

        useCase().addSoftware(SoftwareFormVO.builder().key("myapp").name("名字").build());

        verify(softwareRepository, never()).relateRoles(any(), any());
    }

    @Test
    void removeDeletesFourTablesInVendorOrder() {
        when(commandService.requireExisting("s1"))
                .thenReturn(SoftwareEntity.builder().id("s1").key("myapp").build());

        useCase().removeSoftware("s1");

        InOrder order = inOrder(softwareRepository);
        order.verify(softwareRepository).deleteRoleSoftwareBySoftwareId("s1");
        order.verify(softwareRepository).deleteSoftwareAppBySoftwareId("s1");
        order.verify(softwareRepository).deleteServicePortBySoftwareId("s1");
        order.verify(softwareRepository).deleteById("s1");
    }

    @Test
    void removeChecksExistenceBeforeDeletingAnything() {
        when(commandService.requireExisting("nope")).thenThrow(new WrongArgumentException("software.not.exist.by.id"));

        assertThrows(WrongArgumentException.class, () -> useCase().removeSoftware("nope"));

        verify(softwareRepository, never()).deleteById(any());
    }
}
