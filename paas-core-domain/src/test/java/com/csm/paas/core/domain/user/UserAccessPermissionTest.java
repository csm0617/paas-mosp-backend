package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.user.service.impl.UserAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/user/access_permission?path=`（厂商 `UserService.accessPermission` + `findPath:210-228`）。
 * <p>**六组用例已对拉完全相等**（`findings/93`），其中 `/whatever` → **true** 是照抄厂商那三个
 * "字符类"正则的必然结果（不是我们写错）。
 */
class UserAccessPermissionTest {

    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final IRoleQueryService roleQueryService = mock(IRoleQueryService.class);

    private UserAccessService service(String roleKey) {
        when(roleQueryService.queryRoleList(anyString(), anyInt(), anyInt())).thenReturn(
                RoleListEntity.builder().list(List.of(RoleEntity.builder().id("r1").key(roleKey).build())).total(1).build());
        return new UserAccessService(roleRepository, softwareRepository, roleQueryService);
    }

    /** admin + 命中正则 ⇒ true；`/` ⇒ true。 */
    @Test
    void adminMatchesRegexAndRootPath() {
        var s = service("admin");
        assertTrue(s.checkAccessPermission("admin", "/api/gaop/v1/user/info"));
        assertTrue(s.checkAccessPermission("admin", "/"));
    }

    /**
     * `/whatever` 在厂商那边**也是 true**——因为 `res` 正则里 `[gaop]*`、`[v1]*` 等是**字符类**（可匹配 0 次），
     * 于是它几乎能匹配任何不含特殊字符的短路径。**照抄厂商，不要"修正"这个正则。**
     */
    @Test
    void arbitraryPathAlsoPassesBecauseOfVendorRegexQuirk() {
        assertTrue(service("admin").checkAccessPermission("admin", "/whatever"));
    }

    /** 非 admin 且路径不在允许列表里 ⇒ false。 */
    @Test
    void nonAdminWithoutMatchingPathIsDenied() {
        var s = service("user");
        when(roleRepository.querySoftwareByRoleId("r1")).thenReturn(List.of());

        assertFalse(s.checkAccessPermission("someone", "/api/gaop/v1/user/info"));
    }

    /** 非 admin 但路径**与允许列表全等** ⇒ true（允许列表来自软件的 `key` + 服务路径）。 */
    @Test
    void nonAdminWithExactlyMatchingPathIsAllowed() {
        var s = service("user");
        when(roleRepository.querySoftwareByRoleId("r1")).thenReturn(List.of(
                com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity.builder().appId("s1").build()));
        when(softwareRepository.queryById("s1")).thenReturn(java.util.Optional.of(
                com.csm.paas.core.domain.software.model.entity.SoftwareEntity.builder().id("s1").key("gaop").build()));
        when(softwareRepository.queryServicePathsBySoftware("s1")).thenReturn(List.of("v1/service"));

        assertTrue(s.checkAccessPermission("someone", "/api/gaop/v1/service"));
        assertFalse(s.checkAccessPermission("someone", "/api/gaop/v1/other"));
    }
}
