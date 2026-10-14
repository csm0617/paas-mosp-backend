package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.software.adapter.port.ISoftwareRelationBroadcastPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.service.impl.SoftwareCommandServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/software/softwareRelateRole`（厂商 `SoftwareService.updateSoftwareRelateRole:1535-1561`）。
 * <p>规格见 `docs/findings/135`。重点：入参**去重**；先把该软件**已有**的关联逐条删掉并**每删一条推一次**
 * `/topic/software`；再把 roleKey 解析成 roleId（**查不到的 key 跳过**）补关联；恒返回 true。
 */
class SoftwareRelateRoleTest {

    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final ISoftwareRelationBroadcastPort broadcast = mock(ISoftwareRelationBroadcastPort.class);
    private final IRoleRepository roleRepository = mock(IRoleRepository.class);

    private SoftwareCommandServiceImpl service() {
        return new SoftwareCommandServiceImpl(softwareRepository, broadcast, roleRepository);
    }

    private void roleExists(String key, String id) {
        when(roleRepository.queryByKey(key)).thenReturn(Optional.of(RoleEntity.builder().id(id).key(key).build()));
    }

    @Test
    void deletesExistingRelationsAndBroadcastsEachDeletion() {
        when(softwareRepository.queryRoleIdsBySoftwareId("sw1")).thenReturn(List.of("r-old1", "r-old2"));
        roleExists("dev", "r-dev");

        assertTrue(service().updateSoftwareRelateRole("sw1", List.of("dev")));

        verify(softwareRepository).deleteRoleSoftwareRelation("r-old1", "sw1");
        verify(softwareRepository).deleteRoleSoftwareRelation("r-old2", "sw1");
        verify(broadcast).softwareRelationDeleted("r-old1");
        verify(broadcast).softwareRelationDeleted("r-old2");
        verify(softwareRepository).relateRoles("sw1", List.of("r-dev"));
    }

    @Test
    void duplicateKeysAreDeduplicatedAndUnknownKeysSkipped() {
        when(softwareRepository.queryRoleIdsBySoftwareId("sw1")).thenReturn(List.of());
        roleExists("dev", "r-dev");
        when(roleRepository.queryByKey("ghost")).thenReturn(Optional.empty());

        assertTrue(service().updateSoftwareRelateRole("sw1", List.of("dev", "dev", "ghost")));

        // 去重后只剩 dev + ghost；ghost 查不到 ⇒ 只有 r-dev 进入 relateRoles
        verify(softwareRepository).relateRoles("sw1", List.of("r-dev"));
    }

    @Test
    void emptyOrNullKeyListStillDeletesExistingRelations() {
        when(softwareRepository.queryRoleIdsBySoftwareId("sw1")).thenReturn(List.of("r-old"));

        assertTrue(service().updateSoftwareRelateRole("sw1", List.of()));
        verify(softwareRepository).deleteRoleSoftwareRelation("r-old", "sw1");
        verify(broadcast).softwareRelationDeleted("r-old");
        verify(softwareRepository).relateRoles("sw1", List.of());

        assertTrue(service().updateSoftwareRelateRole("sw1", null));
        verify(softwareRepository, Mockito.times(2)).relateRoles(eq("sw1"), eq(List.of()));
    }

    @Test
    void deletionHappensBeforeInsertion() {
        when(softwareRepository.queryRoleIdsBySoftwareId("sw1")).thenReturn(List.of("r-old"));
        roleExists("dev", "r-dev");

        service().updateSoftwareRelateRole("sw1", List.of("dev"));

        InOrder order = inOrder(softwareRepository);
        order.verify(softwareRepository).queryRoleIdsBySoftwareId("sw1");
        order.verify(softwareRepository).deleteRoleSoftwareRelation("r-old", "sw1");
        order.verify(softwareRepository).relateRoles("sw1", List.of("r-dev"));
    }

    @Test
    void noExistingRelationsMeansNoBroadcast() {
        when(softwareRepository.queryRoleIdsBySoftwareId("sw1")).thenReturn(List.of());
        roleExists("dev", "r-dev");

        service().updateSoftwareRelateRole("sw1", List.of("dev"));

        verify(broadcast, never()).softwareRelationDeleted(anyString());
        verify(softwareRepository, never()).deleteRoleSoftwareRelation(anyString(), anyString());
        verify(softwareRepository).relateRoles(eq("sw1"), any());
    }
}
