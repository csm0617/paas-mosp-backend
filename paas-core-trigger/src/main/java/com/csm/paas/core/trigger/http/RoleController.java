package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.RoleItemDTO;
import com.csm.paas.core.api.dto.RoleListResponseDTO;
import com.csm.paas.core.api.dto.RoleSoftwareDTO;
import com.csm.paas.core.api.dto.RoleSoftwareListDTO;
import com.csm.paas.core.api.dto.RoleFormDTO;
import com.csm.paas.core.api.dto.RoleUsersDTO;
import com.csm.paas.core.cases.role.IRoleDeleteCase;
import com.csm.paas.core.cases.role.IRoleWriteCase;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.trigger.assembler.ExcelResponseWriter;
import com.csm.paas.core.trigger.i18n.MessageResolver;
import com.csm.paas.core.trigger.assembler.RoleExcelWriter;
import com.csm.paas.core.trigger.assembler.RoleListAssembler;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

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
    private IRoleWriteCase roleWriteCase;

    @Resource
    private AuthSupport authSupport;

    @Resource
    private MessageResolver messageResolver;

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

    /**
     * 厂商 `GET /v1/role/exist_role_name?roleName=`：战位名是否已存在（返回布尔）。
     * <p>字面量路径与 `/{id}` 并存时 Spring 优先匹配更具体的字面量，所以不会被 `/{id}` 吃掉。
     * <p>实测提醒：库里战位名是**双重编码**的中文（`docs/findings/11`），拿正常 UTF-8 的中文去查
     * 永远是 `false`——厂商也一样（对拉时三个名字都返回 false）。
     */
    @GetMapping("/exist_role_name")
    public ResponseDTO<Boolean> existRoleName(@RequestParam String roleName) {
        return ResponseDTO.ok(roleQueryService.existsByName(roleName));
    }

    /** 厂商 `GET /v1/role/{id}`：战位详情；不存在时抛厂商同款 `role.storage.not.found`（HTTP 400）。 */
    @GetMapping("/{id}")
    public ResponseDTO<RoleItemDTO> roleDetail(@PathVariable String id) {
        return roleQueryService.queryDetail(id)
                .map(role -> ResponseDTO.ok(RoleListAssembler.toItemDTO(role)))
                .orElseThrow(() -> new WrongArgumentException("role.storage.not.found"));
    }

    /**
     * 厂商 `POST /v1/role`：新增战位。
     * <p>key **或** name 已存在 → 400 `role.storage.existed`；校验与关系写入见 `IRoleWriteCase`。
     * <p>**`softwareList`（软件名列表）本轮未处理**——它需要 `g_software`，属 K2 域（见地图欠账）。
     */
    @PostMapping
    public ResponseDTO<RoleItemDTO> addRole(@RequestBody RoleFormDTO form) {
        return ResponseDTO.ok(RoleListAssembler.toItemDTO(roleWriteCase.addRole(toFormVO(form))));
    }

    /**
     * 厂商 `PUT /v1/role/{id}`：修改战位。
     * <p>保留字保护矩阵（**只要表单带 key 就不许改内置战位**；key 为 `admin`/`dev` 直接被拒）见
     * `IRoleCommandService.prepareEditedRole` 的文档。返回的是重新查出来的行。
     */
    @PutMapping("/{id}")
    public ResponseDTO<RoleItemDTO> editRole(@PathVariable String id, @RequestBody RoleFormDTO form) {
        return ResponseDTO.ok(RoleListAssembler.toItemDTO(roleWriteCase.editRole(id, toFormVO(form))));
    }

    private static com.csm.paas.core.domain.role.model.valobj.RoleFormVO toFormVO(RoleFormDTO form) {
        return com.csm.paas.core.domain.role.model.valobj.RoleFormVO.builder()
                .key(form.getKey()).name(form.getName()).nameFr(form.getNameFr()).nameUs(form.getNameUs())
                .desc(form.getDesc()).type(form.getType())
                .softwareList(form.getSoftwareList()).userList(form.getUserList())
                .subRoleIdList(form.getSubRoleIdList())
                .build();
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
    /**
     * 厂商 `GET /v1/role/{roleId}/software?page&size`：指定战位下的软件列表。
     * <p>**需要管理员**：非管理员（或未登录）→ 400 `role.no.permission.view`（厂商 `RoleController:114-117`）。
     * <p>注意：`appName` 的 i18n 名与 `url` 的两种拼法**尚未实现**（见 `findings/90`）。
     */
    @GetMapping("/{roleId}/software")
    public ResponseDTO<RoleSoftwareListDTO> roleSoftwareList(
            @PathVariable String roleId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        var session = authSupport.currentSession(request);
        // 厂商 userService.isAdmin(user)：战位 key 为 admin 才算管理员（与本项目其它地方的判定一致）
        if (session.isEmpty() || !"admin".equals(session.get().getRoleKey())) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("role.no.permission.view");
        }
        var result = roleQueryService.querySoftwareList(roleId,
                PageUtils.normalizePage(page), PageUtils.normalizeSize(size));
        return ResponseDTO.ok(RoleSoftwareListDTO.builder()
                .list(result.getList().stream()
                        .map(s -> RoleSoftwareDTO.builder()
                                .id(s.getId()).roleId(s.getRoleId()).appId(s.getAppId()).appName(s.getAppName())
                                .url(s.getUrl()).updatedAt(s.getUpdatedAt()).createdAt(s.getCreatedAt())
                                .build())
                        .toList())
                .total(result.getTotal())
                .build());
    }

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

    // ---------- 关联写（厂商 RoleController:337-411 的四个端点）----------

    /**
     * 厂商 `POST /v1/role/{roleId}/users`：**替换式**关联用户（先删光该战位全部用户关联再插）。
     * <p>body 是**裸 JSON 数组**（`["userId",...]`），不是对象。
     * <p>照抄点：战位 key 是 `admin` 时**强制把 `admin` 用户也关联上**；每插一条还会把该用户的
     * `created_at` 覆盖成 now（厂商写错字段，见 `docs/findings/156`）。
     */
    @PostMapping("/{roleId}/users")
    public ResponseDTO<RoleUsersDTO> usersRelateRole(@PathVariable String roleId,
                                                     @RequestBody List<String> userIdList) {
        return ResponseDTO.ok(RoleListAssembler.toRoleUsersDTO(
                roleWriteCase.relateUsers(roleId, userIdList)));
    }

    /**
     * 厂商 `POST /v1/role/{roleId}/suRoles`：**替换式**关联子角色，body 是裸数组的子角色 id。
     * <p>响应是 `Role` 形状（不是 `RoleUsers`）：战位标量 + `subRoleList`，其余三个集合键为 null。
     */
    @PostMapping("/{roleId}/suRoles")
    public ResponseDTO<RoleItemDTO> suRolesRelateRole(@PathVariable String roleId,
                                                      @RequestBody List<String> subRoleIdList) {
        return ResponseDTO.ok(RoleListAssembler.toItemDTO(
                roleWriteCase.relateSubRoles(roleId, subRoleIdList)));
    }

    /**
     * 厂商 `DELETE /v1/role/{roleId}/{userId}`：删单条用户关联，返回 `RoleUsers`。
     * <p>注意与 `DELETE /v1/role?roleId=` 是**两个不同的端点**：这个只删一条关联，不删战位。
     * <p>`admin` 用户 + `admin` 战位 → 400 `user.cannot.remove.ofc.admin`。
     */
    @DeleteMapping("/{roleId}/{userId}")
    public ResponseDTO<RoleUsersDTO> deleteUserInRole(@PathVariable String roleId,
                                                      @PathVariable String userId) {
        return ResponseDTO.ok(RoleListAssembler.toRoleUsersDTO(
                roleWriteCase.removeUserFromRole(roleId, userId)));
    }

    /**
     * 厂商 `DELETE /v1/role/{roleId}/app/{appId}`：删单条"战位-软件"关联，返回 `data:null`。
     * <p>删完会往 `/topic/software` 推 `{event:"DELETE", roleList:<roleId>}`（厂商 `roleRemoveApp`）。
     */
    @DeleteMapping("/{roleId}/app/{appId}")
    public ResponseDTO<Void> deleteRoleApp(@PathVariable String roleId, @PathVariable String appId) {
        roleWriteCase.removeAppFromRole(roleId, appId);
        return ResponseDTO.ok(null);
    }

    // ---------- Excel（厂商 RoleController:263-305）----------

    /**
     * 厂商 `POST /v1/role/backup`：导出战位名单（**xls 附件**，body 是战位 id 数组，可为空 ⇒ 全量）。
     *
     * <p>8 列：名称/唯一标识/关联应用(JSON)/关联子角色(JSON)/描述/关联用户(JSON)/英文名/法文名；
     * **排除 `admin`/`dev`/`user` 三个内置战位**；`created_at` 倒序；**不设列宽**。
     * <p>与 `POST /v1/role/export` 是同一段实现（厂商两个方法体逐字相同，只有审计 opType 不同）。
     * 规则见 `IRoleQueryService.queryExportRows`（规格 `findings/157`）。
     */
    @PostMapping("/backup")
    public void backUpRole(@RequestBody(required = false) List<String> roleIdList,
                           HttpServletResponse response) throws IOException {
        var rows = roleQueryService.queryExportRows(roleIdList);
        ExcelResponseWriter.write(response, RoleExcelWriter.buildExport(messageResolver, rows),
                messageResolver, "export.file.name.role.export", false);
    }

    /** 厂商 `POST /v1/role/export`：与 {@link #backUpRole} **逐字相同**（前端不调，但厂商 API 面上有）。 */
    @PostMapping("/export")
    public void exportRole(@RequestBody(required = false) List<String> roleIdList,
                           HttpServletResponse response) throws IOException {
        var rows = roleQueryService.queryExportRows(roleIdList);
        ExcelResponseWriter.write(response, RoleExcelWriter.buildExport(messageResolver, rows),
                messageResolver, "export.file.name.role.export", false);
    }

    /**
     * 厂商 `GET /v1/role/import/template`：战位导入模板下载（xls 附件）。
     * <p>8 列表头 + **6** 个示例单元格；与子角色的模板不同，**这里厂商没有设 CORS 头**。
     */
    @GetMapping("/import/template")
    public void roleImportTemplate(HttpServletResponse response) throws IOException {
        ExcelResponseWriter.write(response, RoleExcelWriter.buildTemplate(messageResolver),
                messageResolver, "export.file.name.role.import.template", false);
    }
}
