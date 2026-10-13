package com.csm.paas.core.domain.subrole;

import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleListEntity;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleRoleVO;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleUserVO;
import com.csm.paas.core.domain.subrole.service.impl.SubRoleQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 子角色列表的领域规则（离线）。
 * <p>规则来源：厂商 `SubRoleServiceImpl.switchRoleList`（`SubRoleServiceImpl.java:185-204`）。
 * 关键是两条分支的**语义差异**：空关键字走分页 + 取分页总数；非空关键字**不分页**且 `total = list.size()`。
 */
class SubRoleQueryServiceImplTest {

    private final ISubRoleRepository repository = mock(ISubRoleRepository.class);

    private SubRoleQueryServiceImpl service() {
        return new SubRoleQueryServiceImpl(repository);
    }

    @Test
    void blankNameUsesPagingAndCountTotal() {
        when(repository.queryPageOrderByCreatedAtDesc(0, 20)).thenReturn(List.of(subRole("s1", "sr-a")));
        when(repository.count()).thenReturn(7L);

        SubRoleListEntity result = service().querySubRoleList(1, 20, null);

        assertEquals(1, result.getList().size());
        assertEquals(7L, result.getTotal(), "空关键字分支：total 来自分页总数");
        verify(repository, never()).queryByNameLike(anyString());
    }

    @Test
    void blankNameAlsoCoversWhitespaceOnlyInput() {
        when(repository.queryPageOrderByCreatedAtDesc(0, 20)).thenReturn(List.of());
        when(repository.count()).thenReturn(0L);

        service().querySubRoleList(1, 20, "   ");

        verify(repository).queryPageOrderByCreatedAtDesc(0, 20);
    }

    @Test
    void nonBlankNameDoesNotPageAndTotalIsListSize() {
        when(repository.queryByNameLike("%角色%")).thenReturn(List.of(subRole("s1", "sr-a"), subRole("s2", "sr-b")));

        SubRoleListEntity result = service().querySubRoleList(1, 20, "角色");

        assertEquals(2, result.getList().size());
        assertEquals(2L, result.getTotal(), "非空关键字分支：厂商漏了 startPage，total = list.size()（不分页）");
        verify(repository, never()).queryPageOrderByCreatedAtDesc(anyInt(), anyInt());
    }

    @Test
    void relationsAreFilledForEachRow() {
        when(repository.queryPageOrderByCreatedAtDesc(0, 20)).thenReturn(List.of(subRole("s1", "sr-a")));
        when(repository.count()).thenReturn(1L);
        when(repository.queryUsersBySubRoleId("s1"))
                .thenReturn(List.of(SubRoleUserVO.builder().userId("u1").userName("admin").build()));
        when(repository.queryRolesBySubRoleId("s1"))
                .thenReturn(List.of(SubRoleRoleVO.builder().roleId("r1").roleKey("admin").roleName("运维管理员门户").build()));

        SubRoleListEntity result = service().querySubRoleList(1, 20, null);

        assertEquals(1, result.getList().get(0).getUserList().size());
        assertEquals("admin", result.getList().get(0).getUserList().get(0).getUserName());
        assertEquals("运维管理员门户", result.getList().get(0).getRoleList().get(0).getRoleName());
    }

    @Test
    void relationsAreEmptyArraysNotNull() {
        when(repository.queryPageOrderByCreatedAtDesc(0, 20)).thenReturn(List.of(subRole("s1", "sr-a")));
        when(repository.count()).thenReturn(1L);
        when(repository.queryUsersBySubRoleId("s1")).thenReturn(List.of());
        when(repository.queryRolesBySubRoleId("s1")).thenReturn(List.of());

        SubRoleListEntity result = service().querySubRoleList(1, 20, null);

        assertTrue(result.getList().get(0).getUserList().isEmpty());
        assertTrue(result.getList().get(0).getRoleList().isEmpty());
    }

    private static SubRoleEntity subRole(String id, String key) {
        return SubRoleEntity.builder().id(id).key(key).name("子角色" + id).build();
    }
}
