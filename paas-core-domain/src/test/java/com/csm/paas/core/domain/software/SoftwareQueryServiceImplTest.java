package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareListEntity;
import com.csm.paas.core.domain.software.model.valobj.RelationRoleVO;
import com.csm.paas.core.domain.software.service.impl.SoftwareQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 软件列表的领域规则（离线）。规则来源：厂商 `SoftwareService.getSoftwareList`（`:483-547`）
 * 与 `setServiceStatusBySoftware`（`:564-585`）。
 */
class SoftwareQueryServiceImplTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);
    private final IWebEntryStatusPort webEntryStatusPort = mock(IWebEntryStatusPort.class);

    private SoftwareQueryServiceImpl service() {
        return new SoftwareQueryServiceImpl(repository, webEntryStatusPort);
    }

    @Test
    void emptyResultSkipsAllEnrichment() {
        when(repository.queryPage(any(), any(), anyString(), eq(true), eq(0), eq(20))).thenReturn(List.of());
        when(repository.count(any(), any())).thenReturn(0L);

        SoftwareListEntity result = service().querySoftwareList(1, 20, null, null, "key", "asc", null);

        assertTrue(result.getSoftwareList().isEmpty());
        assertEquals(0L, result.getTotal());
        verify(webEntryStatusPort, never()).queryWebStatus(any());
    }

    @Test
    void csTypeGetsGaopUrlOtherwiseRouterKey() {
        when(repository.queryPage(any(), any(), anyString(), eq(true), eq(0), eq(20)))
                .thenReturn(List.of(software("1", "csApp", "CS"), software("2", "webApp", "WEB")));
        when(repository.count(any(), any())).thenReturn(2L);
        when(repository.queryWebEntryServiceNames(anyString())).thenReturn(List.of());
        when(repository.queryRoleIdsBySoftwareId(anyString())).thenReturn(List.of());
        when(repository.existsWhiteListPath(anyString())).thenReturn(false);

        List<com.csm.paas.core.domain.software.model.entity.SoftwareMessageEntity> list =
                service().querySoftwareList(1, 20, null, null, "key", "asc", "myhost").getSoftwareList();

        assertEquals("gaop://cs?name=名字&id=1&key=csApp&version=v&execName=null&remoteIp=null"
                        + "&remoteName=null&remotePassword=null&host=myhost",
                list.get(0).getUrl(), "CS 类型拼 gaop:// 串（null 变成字面量 null，照抄厂商的字符串拼接）");
        assertEquals("/router/2", list.get(1).getUrl(), "非 CS 类型用 router_key");
    }

    @Test
    void releaseStatusComesFromWhiteListPath() {
        when(repository.queryPage(any(), any(), anyString(), eq(true), eq(0), eq(20)))
                .thenReturn(List.of(software("1", "relApp", "WEB")));
        when(repository.count(any(), any())).thenReturn(1L);
        when(repository.queryWebEntryServiceNames(anyString())).thenReturn(List.of());
        when(repository.queryRoleIdsBySoftwareId(anyString())).thenReturn(List.of());
        when(repository.existsWhiteListPath("/relApp/**")).thenReturn(true);

        assertEquals(1, service().querySoftwareList(1, 20, null, null, "key", "asc", null)
                .getSoftwareList().get(0).getReleaseStatus());
        verify(repository).existsWhiteListPath("/relApp/**");
    }

    @Test
    void webIsZeroWithoutEntryRowTwoWhenDeploymentMissingOneWhenFound() {
        when(repository.count(any(), any())).thenReturn(1L);
        when(repository.existsWhiteListPath(anyString())).thenReturn(false);
        when(repository.queryRoleIdsBySoftwareId(anyString())).thenReturn(List.of());

        // 0：没有 web 入口行
        when(repository.queryPage(any(), any(), anyString(), eq(true), eq(0), eq(20)))
                .thenReturn(List.of(software("1", "a", "WEB")));
        when(repository.queryWebEntryServiceNames("1")).thenReturn(List.of());
        assertEquals(0, webOf("a"));

        // 2：有入口行但 Deployment 找不到（端口返回空 map）
        when(repository.queryWebEntryServiceNames("1")).thenReturn(List.of("svc-x"));
        when(webEntryStatusPort.queryWebStatus(List.of("svc-x"))).thenReturn(Map.of());
        assertEquals(2, webOf("a"));

        // 1：Deployment 找到了且状态健康
        when(webEntryStatusPort.queryWebStatus(List.of("svc-x"))).thenReturn(Map.of("svc-x", 1));
        assertEquals(1, webOf("a"));
    }

    @Test
    void roleListIsNullWhenNoRelation() {
        when(repository.queryPage(any(), any(), anyString(), eq(true), eq(0), eq(20)))
                .thenReturn(List.of(software("1", "a", "WEB")));
        when(repository.count(any(), any())).thenReturn(1L);
        when(repository.queryWebEntryServiceNames(anyString())).thenReturn(List.of());
        when(repository.queryRoleIdsBySoftwareId("1")).thenReturn(List.of());

        assertNull(service().querySoftwareList(1, 20, null, null, "key", "asc", null)
                .getSoftwareList().get(0).getRoleList(), "无关联时是 null，不是空数组");
    }

    @Test
    void roleListIsFilledWhenRelated() {
        when(repository.queryPage(any(), any(), anyString(), eq(true), eq(0), eq(20)))
                .thenReturn(List.of(software("1", "a", "WEB")));
        when(repository.count(any(), any())).thenReturn(1L);
        when(repository.queryWebEntryServiceNames(anyString())).thenReturn(List.of());
        when(repository.queryRoleIdsBySoftwareId("1")).thenReturn(List.of("r1"));
        when(repository.queryRelationRoles(List.of("r1")))
                .thenReturn(List.of(RelationRoleVO.builder().id("r1").name("运维管理员门户").key("admin").build()));

        assertEquals(1, service().querySoftwareList(1, 20, null, null, "key", "asc", null)
                .getSoftwareList().get(0).getRoleList().size());
    }

    private int webOf(String ignored) {
        return service().querySoftwareList(1, 20, null, null, "key", "asc", null)
                .getSoftwareList().get(0).getWeb();
    }

    private static SoftwareEntity software(String id, String key, String type) {
        return SoftwareEntity.builder().id(id).key(key).name("名字").type(type)
                .version("v").routerKey("/router/" + id).orderTop(0).build();
    }
}
