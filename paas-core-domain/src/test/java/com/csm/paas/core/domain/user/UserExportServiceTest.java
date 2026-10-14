package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.service.impl.UserExportService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 导出行的取值与跳过规则（厂商 `generateExportExcel:977-1026`）。
 * <p>**跳过 admin 与 root**（**不跳 dev**，与 `generateBackupExcel` 不同）。
 */
class UserExportServiceTest {

    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final ISubRoleRepository subRoleRepository = mock(ISubRoleRepository.class);

    private UserExportService service() {
        return new UserExportService(userRepository, roleRepository, subRoleRepository);
    }

    private void users(UserEntity... users) {
        when(userRepository.queryPageByKeyword(any(), anyInt(), anyInt())).thenReturn(List.of(users));
        when(roleRepository.queryByUsernameAndType(anyString(), anyInt())).thenReturn(List.of());
        when(subRoleRepository.queryByUserId(anyString())).thenReturn(List.of());
    }

    @Test
    void skipsAdminAndRootButKeepsDev() {
        users(UserEntity.builder().id("1").username("admin").build(),
                UserEntity.builder().id("2").username("root").build(),
                UserEntity.builder().id("3").username("dev").build(),
                UserEntity.builder().id("4").username("bob").build());

        var rows = service().exportRows(List.of());

        assertEquals(List.of("dev", "bob"), rows.stream().map(UserExportService.Row::username).toList(),
                "跳过 admin/root，但**不跳 dev**");
    }

    @Test
    void rowCarriesEightVendorFields() {
        users(UserEntity.builder().id("4").username("bob").nickname("尼克").desc("描述")
                .status(1).password("$2a$hash").build());
        when(roleRepository.queryByUsernameAndType("bob", 1)).thenReturn(List.of(
                RoleEntity.builder().id("r1").key("admin").build()));
        when(roleRepository.queryByUsernameAndType("bob", 2)).thenReturn(List.of(
                RoleEntity.builder().id("r2").key("ops").build()));
        when(subRoleRepository.queryByUserId("4")).thenReturn(List.of(
                SubRoleEntity.builder().id("s1").key("sub1").build()));

        var row = service().exportRows(List.of()).get(0);

        assertEquals("bob", row.username());
        assertEquals("尼克", row.nickname());
        assertEquals("admin", row.roleKey(), "第 2 列 = **第一个**角色的 key");
        assertEquals(List.of("ops"), row.roleKeys(), "第 3 列 = 角色关系 key 列表（JSON 由 trigger 层序列化）");
        assertEquals(List.of("sub1"), row.subRoleKeys(), "第 4 列 = 子角色 key 列表");
        assertEquals("描述", row.desc());
        assertEquals(1, row.status());
        assertEquals("$2a$hash", row.password(), "第 7 列是口令哈希（照抄厂商，见 deviations B12）");
    }

    @Test
    void idListFiltersUsers() {
        users(UserEntity.builder().id("4").username("bob").build(),
                UserEntity.builder().id("5").username("alice").build());

        var rows = service().exportRows(List.of("5"));

        assertEquals(List.of("alice"), rows.stream().map(UserExportService.Row::username).toList());
    }

    @Test
    void emptyWhenOnlySkippedUsersExist() {
        users(UserEntity.builder().id("1").username("admin").build());
        assertTrue(service().exportRows(List.of()).isEmpty());
    }
}
