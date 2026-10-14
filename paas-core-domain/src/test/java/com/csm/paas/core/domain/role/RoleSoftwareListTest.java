package com.csm.paas.core.domain.role;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.service.impl.RoleQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/role/{roleId}/software`（厂商 `RoleService.roleSoftwareList:416-418`）的分页与排序口径。
 * <p>库里 `g_role_software` 为空 ⇒ 只能验"分页/总数/排序声明"这一层；`appName` 的 i18n 与 `url` 的两种拼法
 * 尚未实现（`findings/90`）。
 */
class RoleSoftwareListTest {

    private final IRoleRepository repository = mock(IRoleRepository.class);

    private RoleQueryServiceImpl service() {
        return new RoleQueryServiceImpl(repository,
                mock(com.csm.paas.core.domain.role.adapter.repository.IRoleOnlineUserRepository.class),
                mock(com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository.class));
    }

    @Test
    void pagingUsesOffsetAndReturnsTotalFromCountQuery() {
        when(repository.querySoftwarePage("r1", 0, 20)).thenReturn(List.of(
                RoleSoftwareEntity.builder().id(1L).roleId("r1").appId("s1").build()));
        when(repository.countSoftwareByRoleId("r1")).thenReturn(1L);

        var result = service().querySoftwareList("r1", 1, 20);

        verify(repository).querySoftwarePage("r1", 0, 20);
        assertEquals(1, result.getList().size());
        assertEquals(1L, result.getTotal(), "total 来自独立的 count 查询（不受分页影响）");
    }

    @Test
    void secondPageUsesCorrectOffset() {
        when(repository.querySoftwarePage(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.countSoftwareByRoleId(anyString())).thenReturn(0L);

        service().querySoftwareList("r1", 3, 10);

        verify(repository).querySoftwarePage("r1", 20, 10);
    }
}
