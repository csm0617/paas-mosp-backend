package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.RoleItemDTO;
import com.csm.paas.core.api.dto.RoleListResponseDTO;
import com.csm.paas.core.api.dto.RoleSoftwareDTO;
import com.csm.paas.core.api.dto.RoleSoftwareListDTO;
import com.csm.paas.core.cases.role.IRoleDeleteCase;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.trigger.assembler.RoleListAssembler;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `/v1/role` 的重写版（切片 3b）。
 *
 * <p>四个读端点的**集合填充各不相同**，是本类最容易做错的地方（实测样本见
 * `docs/findings/runtime-samples/v1_role*.json`）：
 * <ul>
 *   <li>`GET /v1/role`：只填 `softwareList`；`onLineUserList`/`userList`/`subRoleList` 全 null；</li>
 *   <li>`GET /v1/role/list`：多填 `userList`；</li>
 *   <li>`GET /v1/role/switchRole`：四个集合**全填**（`onLineUserList` 来自 Redis）；</li>
 *   <li>`GET /v1/role/{id}`：四个集合全 null，但 `nameFr`/`nameUs` 兜底成 `name`。</li>
 * </ul>
 *
 * <p>**鉴权边界**：`/v1/role`、`/v1/role/list`、`/v1/role/switchRole`、`/v1/role/{id}` 实测**无 cookie 也是 200**
 * （鉴权在网关侧，见 `docs/findings/14-vendor-auth-is-gateway-side.md`）；
 * 但 `/v1/role/{roleId}/software` **要会话**（无 cookie 是 401），两者不能一起处理。
 */
@RestController
@RequestMapping("/v1/role")
public class RoleController {

    @Resource
    private IRoleQueryService roleQueryService;

    @Resource
    private IRoleDeleteCase roleDeleteCase;

    @Resource
    private AuthSupport authSupport;

    /** 厂商 `GET /v1/role?page&size&username`：username 非空时按该用户关联的战位过滤（且不分页）。 */
    @GetMapping
    public ResponseDTO<RoleListResponseDTO> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String username) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        return ResponseDTO.ok(RoleListAssembler.toResponse(
                roleQueryService.queryRoleList(username, normalizedPage, normalizedSize)));
    }

    /** 厂商 `GET /v1/role/list?page&size&roleName`：全量或按名模糊，**带 userList**。 */
    @GetMapping("/list")
    public ResponseDTO<RoleListResponseDTO> listAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String roleName) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        String normalizedName = roleName == null ? null : roleName.replaceAll(" +", "");
        return ResponseDTO.ok(RoleListAssembler.toResponse(
                roleQueryService.queryAllRoleList(normalizedName, normalizedPage, normalizedSize)));
    }

    /** 厂商 `GET /v1/role/switchRole?page&size&roleName&type`：按 `type` 分战位(1)/角色(2)。 */
    @GetMapping("/switchRole")
    public ResponseDTO<RoleListResponseDTO> switchRoleList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String roleName,
            @RequestParam Integer type) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        String normalizedName = roleName == null ? null : roleName.replaceAll(" +", "");
        return ResponseDTO.ok(RoleListAssembler.toResponse(
                roleQueryService.querySwitchRoleList(normalizedName, normalizedPage, normalizedSize, type)));
    }

    /** 厂商 `GET /v1/role/{id}`：战位详情；不存在时抛厂商同款 `role.storage.not.found`（HTTP 400）。 */
    @GetMapping("/{id}")
    public ResponseDTO<RoleItemDTO> roleDetail(@PathVariable String id) {
        return roleQueryService.queryDetail(id)
                .map(role -> ResponseDTO.ok(RoleListAssembler.toItemDTO(role)))
                .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
    }

    /**
     * 厂商 `DELETE /v1/role?roleId=a,b,c`：批量删除战位（含 6 表级联，**整体一个事务**）。
     * <p>厂商返回 `Response.ok(null)` → `{"msg":"OK","code":0,"data":null}`。
     * <p>内置战位 `admin`/`dev` 与不存在的 id 分别抛 `role.cannot.delete.storage` / `role.missing.storage`（HTTP 400）。
     */
    @DeleteMapping
    public ResponseDTO<Void> deleteRole(@RequestParam String roleId) {
        roleDeleteCase.deleteRoles(java.util.Arrays.asList(roleId.split(",")));
        return ResponseDTO.ok(null);
    }

    /**
     * 厂商 `GET /v1/role/software`：**当前会话战位**下的软件列表。
     * <p>与厂商一致：读 `g_role_software`（本环境为空）。
     */
    @GetMapping("/software")
    public ResponseDTO<RoleSoftwareListDTO> currentRoleSoftware(HttpServletRequest request) {
        String roleKey = authSupport.currentSession(request)
                .map(com.csm.paas.core.domain.user.model.valobj.SessionVO::getRoleKey)
                .orElse(null);
        if (roleKey == null) {
            return ResponseDTO.ok(RoleSoftwareListDTO.builder().list(java.util.List.of()).total(0).build());
        }
        java.util.List<RoleSoftwareDTO> list = roleQueryService.queryByKey(roleKey)
                .map(role -> roleQueryService.querySoftwareByRoleId(role.getId()).stream()
                        .map(s -> RoleSoftwareDTO.builder()
                                .id(s.getId()).roleId(s.getRoleId()).appId(s.getAppId()).appName(s.getAppName())
                                .url(s.getUrl()).updatedAt(s.getUpdatedAt()).createdAt(s.getCreatedAt())
                                .build())
                        .toList())
                .orElse(java.util.List.of());
        return ResponseDTO.ok(RoleSoftwareListDTO.builder().list(list).total(list.size()).build());
    }
}
