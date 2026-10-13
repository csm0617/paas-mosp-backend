package com.csm.paas.core.domain.role;

import com.csm.paas.core.domain.role.adapter.repository.IRoleOnlineUserRepository;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.model.valobj.OnlineUserVO;
import com.csm.paas.core.domain.role.model.valobj.RoleUserVO;
import com.csm.paas.core.domain.role.service.impl.RoleQueryServiceImpl;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 战位查询的领域规则（离线）。规则来源：厂商 `RoleService.java:172-399`、`518-521`、`Role.java:33-43`。
 * <p>四个读端点共用一套实体，差别全在**哪些集合被填**；这里把差别逐条钉住。
 */
class RoleQueryServiceImplTest {

    private static final String ADMIN_ROLE_ID = "r-admin";

    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final IRoleOnlineUserRepository onlineUserRepository = mock(IRoleOnlineUserRepository.class);
    private final ISubRoleRepository subRoleRepository = mock(ISubRoleRepository.class);

    private RoleQueryServiceImpl service() {
        return new RoleQueryServiceImpl(roleRepository, onlineUserRepository, subRoleRepository);
    }

    @Test
    void roleListDoesNotFillUserList() {
        when(roleRepository.queryPage(0, 20)).thenReturn(List.of(role(ADMIN_ROLE_ID, "admin", 1)));
        when(roleRepository.count()).thenReturn(1L);
        when(roleRepository.querySoftwareByRoleId(ADMIN_ROLE_ID)).thenReturn(List.of());

        RoleListEntity result = service().queryRoleList(null, 1, 20);

        assertNull(result.getList().get(0).getUserList(), "/v1/role 不填 userList（厂商实测为 null）");
        assertNull(result.getList().get(0).getOnLineUserList(), "/v1/role 不填 onLineUserList");
        assertNull(result.getList().get(0).getSubRoleList(), "/v1/role 不填 subRoleList");
        verify(roleRepository, never()).queryUsersByRoleId(anyString());
    }

    @Test
    void roleListFillsUserList() {
        when(roleRepository.queryPage(0, 20)).thenReturn(List.of(role(ADMIN_ROLE_ID, "admin", 1)));
        when(roleRepository.count()).thenReturn(1L);
        when(roleRepository.querySoftwareByRoleId(ADMIN_ROLE_ID)).thenReturn(List.of());
        when(roleRepository.queryUsersByRoleId(ADMIN_ROLE_ID))
                .thenReturn(List.of(RoleUserVO.builder().userId("u1").userName("admin").build()));

        RoleListEntity result = service().queryAllRoleList(null, 1, 20);

        assertEquals(1, result.getList().get(0).getUserList().size(), "/v1/role/list 必须填 userList");
        assertNull(result.getList().get(0).getSubRoleList(), "但 /v1/role/list 仍不填 subRoleList");
    }

    @Test
    void switchRoleFiltersByTypeAndUsesCountTotal() {
        when(roleRepository.queryPageByType(1, 0, 20)).thenReturn(List.of(role(ADMIN_ROLE_ID, "admin", 1)));
        when(roleRepository.countByType(1)).thenReturn(3L);

        RoleListEntity result = service().querySwitchRoleList(null, 1, 20, 1);

        assertEquals(3L, result.getTotal(), "空关键字分支：total 取分页总数");
        verify(roleRepository).queryPageByType(1, 0, 20);
    }

    @Test
    void switchRoleWithNameDoesNotPageAndTotalIsListSize() {
        when(roleRepository.queryByTypeAndNameLike(2, "%操作%"))
                .thenReturn(List.of(role("r-user", "user", 2), role("r-other", "other", 2)));

        RoleListEntity result = service().querySwitchRoleList("操作", 1, 20, 2);

        assertEquals(2L, result.getTotal(), "非空关键字分支：total = list.size()（厂商漏了 startPage）");
        verify(roleRepository, never()).queryPageByType(anyInt(), anyInt(), anyInt());
    }

    @Test
    void switchRoleFillsAllFourCollectionsIncludingOnlineUsers() {
        when(roleRepository.queryPageByType(1, 0, 20)).thenReturn(List.of(role(ADMIN_ROLE_ID, "admin", 1)));
        when(roleRepository.countByType(1)).thenReturn(1L);
        when(roleRepository.querySoftwareByRoleId(ADMIN_ROLE_ID)).thenReturn(List.of());
        when(roleRepository.queryUsersByRoleId(ADMIN_ROLE_ID))
                .thenReturn(List.of(RoleUserVO.builder().userId("u1").userName("admin").build()));
        when(roleRepository.querySubRoleIdsByRoleId(ADMIN_ROLE_ID)).thenReturn(List.of());
        when(onlineUserRepository.queryOnlineUsers()).thenReturn(List.of(
                OnlineUserVO.builder().username("admin").roleKey("admin").build(),
                OnlineUserVO.builder().username("bob").roleKey("dev").build()));

        RoleEntity result = service().querySwitchRoleList(null, 1, 20, 1).getList().get(0);

        assertEquals(List.of("admin"), result.getOnLineUserList(), "只归组该战位 key 的在线用户");
        assertEquals(1, result.getUserList().size());
        assertTrue(result.getSubRoleList().isEmpty(), "无关联时是空数组");
    }

    @Test
    void switchRoleGivesEmptyOnlineListWhenNobodyIsOnline() {
        when(roleRepository.queryPageByType(2, 0, 20)).thenReturn(List.of(role("r-user", "user", 2)));
        when(roleRepository.countByType(2)).thenReturn(1L);
        when(roleRepository.querySoftwareByRoleId("r-user")).thenReturn(List.of());
        when(roleRepository.queryUsersByRoleId("r-user")).thenReturn(List.of());
        when(roleRepository.querySubRoleIdsByRoleId("r-user")).thenReturn(List.of());
        when(onlineUserRepository.queryOnlineUsers()).thenReturn(List.of());

        RoleEntity result = service().querySwitchRoleList(null, 1, 20, 2).getList().get(0);

        assertTrue(result.getOnLineUserList().isEmpty(), "无人在线是 [] 而不是 null");
    }

    @Test
    void adminPortalOverridesSoftwareListEvenWhenTableHasRows() {
        when(roleRepository.queryPage(0, 20)).thenReturn(List.of(role(ADMIN_ROLE_ID, "admin", 1)));
        when(roleRepository.count()).thenReturn(1L);
        when(roleRepository.querySoftwareByRoleId(ADMIN_ROLE_ID))
                .thenReturn(List.of(RoleSoftwareEntity.builder().id(9L).appId("real").appName("真实软件").build()));

        RoleEntity result = service().queryRoleList(null, 1, 20).getList().get(0);

        assertEquals(1, result.getSoftwareList().size(), "admin 的软件列表被硬编码覆盖成单条伪条目");
        assertEquals(0L, result.getSoftwareList().get(0).getId());
        assertEquals("admin", result.getSoftwareList().get(0).getAppId());
        assertEquals("运维管理员门户", result.getSoftwareList().get(0).getAppName());
        assertTrue(result.getAdmin());
        assertTrue(result.getAdminOrDev());
    }

    @Test
    void devPortalHasItsOwnNameAndFlags() {
        when(roleRepository.queryPage(0, 20)).thenReturn(List.of(role("r-dev", "dev", 1)));
        when(roleRepository.count()).thenReturn(1L);
        when(roleRepository.querySoftwareByRoleId("r-dev")).thenReturn(List.of());

        RoleEntity result = service().queryRoleList(null, 1, 20).getList().get(0);

        assertEquals("开发管理员门户", result.getSoftwareList().get(0).getAppName());
        assertTrue(result.getDev());
        assertTrue(result.getAdminOrDev());
        assertTrue(!result.getAdmin());
    }

    @Test
    void ordinaryRoleKeepsItsOwnSoftwareListAndFalseFlags() {
        when(roleRepository.queryPage(0, 20)).thenReturn(List.of(role("r-user", "user", 2)));
        when(roleRepository.count()).thenReturn(1L);
        when(roleRepository.querySoftwareByRoleId("r-user"))
                .thenReturn(List.of(RoleSoftwareEntity.builder().id(7L).appId("app1").appName("应用1").build()));

        RoleEntity result = service().queryRoleList(null, 1, 20).getList().get(0);

        assertEquals(7L, result.getSoftwareList().get(0).getId(), "非 admin/dev 不覆盖");
        assertTrue(!result.getAdmin());
        assertTrue(!result.getAdminOrDev());
    }

    @Test
    void detailFallsBackNameUsAndNameFrToNameAndDoesNotOverrideSoftware() {
        when(roleRepository.queryById(ADMIN_ROLE_ID)).thenReturn(Optional.of(role(ADMIN_ROLE_ID, "admin", 1)));

        RoleEntity result = service().queryDetail(ADMIN_ROLE_ID).orElseThrow();

        assertEquals("运维管理员门户", result.getNameUs(), "厂商把 nameUs 兜底成 name");
        assertEquals("运维管理员门户", result.getNameFr());
        assertNull(result.getSoftwareList(), "/v1/role/{id} 不做伪门户覆盖（实测 softwareList 为 null）");
        assertNull(result.getOnLineUserList());
        assertTrue(result.getAdmin(), "三个布尔是计算 getter，恒有值");
    }

    @Test
    void detailIsEmptyWhenRoleDoesNotExist() {
        when(roleRepository.queryById("nope")).thenReturn(Optional.empty());

        assertTrue(service().queryDetail("nope").isEmpty());
    }

    @Test
    void usernameFilterDoesNotPageAndUsesListSizeAsTotal() {
        when(roleRepository.queryByUsername("admin")).thenReturn(List.of(role(ADMIN_ROLE_ID, "admin", 1)));
        when(roleRepository.querySoftwareByRoleId(ADMIN_ROLE_ID)).thenReturn(List.of());

        RoleListEntity result = service().queryRoleList("admin", 1, 20);

        assertEquals(1L, result.getTotal());
        verify(roleRepository, never()).queryPage(anyInt(), anyInt());
    }

    private static RoleEntity role(String id, String key, int type) {
        return RoleEntity.builder()
                .id(id).key(key).name("运维管理员门户").desc("描述").type(type)
                .updatedAt(1L).createdAt(1L)
                .build();
    }
}
