package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.RoleRelationDTO;
import com.csm.paas.core.api.dto.TenxUserInfoDTO;
import com.csm.paas.core.api.dto.LoginUserDTO;
import com.csm.paas.core.api.dto.UserDTO;
import com.csm.paas.core.api.dto.UserDetailDTO;
import com.csm.paas.core.api.dto.UserFormDTO;
import com.csm.paas.core.api.dto.UserListByRoleDTO;
import com.csm.paas.core.api.dto.UserInfoListResponseDTO;
import com.csm.paas.core.api.dto.UserLoginItemDTO;
import com.csm.paas.core.api.dto.UserLoginListDTO;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;
import com.csm.paas.core.domain.user.model.valobj.UserCallerVO;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;
import com.csm.paas.core.domain.user.service.IAuthService;
import com.csm.paas.core.cases.user.IUserAddCase;
import com.csm.paas.core.cases.user.IUserUpdateCase;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.service.IUserCommandService;
import com.csm.paas.core.domain.user.service.IUserQueryService;
import com.csm.paas.core.trigger.assembler.LoginAssembler;
import com.csm.paas.core.trigger.assembler.UserAssembler;
import com.csm.paas.core.trigger.assembler.UserDetailAssembler;
import com.csm.paas.core.trigger.assembler.UserInfoAssembler;
import com.csm.paas.core.types.enums.ResponseCode;
import com.csm.paas.core.types.exception.BusinessException;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `GET /v1/user`：当前登录用户（前端布局启动时就会调它）。
 * <p>未登录返回厂商同款 401（由 {@link GlobalExceptionHandler} 翻译）。
 */
@RestController
public class UserController {

    @Resource
    private AuthSupport authSupport;

    @Resource
    private com.csm.paas.core.domain.user.service.IUserAccessService userAccessService;

    @Resource
    private com.csm.paas.core.domain.user.service.IUserRolesQueryService userRolesQueryService;

    @Resource
    private com.csm.paas.core.trigger.i18n.MessageResolver messageResolver;

    @Resource
    private com.csm.paas.core.domain.user.service.impl.UserExportService userExportService;

    @Resource
    private com.csm.paas.core.domain.user.service.IUserAvatarService userAvatarService;

    @Resource
    private com.csm.paas.core.domain.user.service.IUserImportService userImportService;

    @Resource
    private com.csm.paas.core.domain.user.service.ITenxUserQueryService tenxUserQueryService;

    @Resource
    private IUserRepository userRepository;

    @Resource
    private IAuthService authService;

    @Resource
    private IUserQueryService userQueryService;

    @Resource
    private IUserCommandService userCommandService;

    @Resource
    private IUserAddCase userAddCase;

    @Resource
    private IUserUpdateCase userUpdateCase;

    /**
     * 厂商 `GET /v1/user/info`：用户管理列表（用户 + 关联战位 + 关联子角色）。
     * <p>**公开**：实测厂商不带 cookie 也 200（鉴权在网关侧，见 `docs/findings/14-vendor-auth-is-gateway-side.md`）。
     * <p>分页归一化照抄厂商 `SqlUtils`；关键字先去掉空格（厂商 `UserController.java:576`）。
     */
    /**
     * 厂商 `GET /v1/user/access_permission?path=`：该路径是否有权限（返回布尔）。
     * <p>厂商在**没有 Cookie** 时会 **NPE（500）**——它本意是抛 `user.not.logged.in.or.expired`
     * （判空写在了 `new UserLoginInfo()` 之后，永远不成立）。我们**实现其本意**：400 `user.not.logged.in.or.expired`
     * （记为 B 类偏离，见 `findings/92`）。
     */
    @GetMapping("/v1/user/access_permission")
    public ResponseDTO<Boolean> accessPermission(
            @org.springframework.web.bind.annotation.RequestParam("path") String path,
            HttpServletRequest request) {
        var session = authSupport.currentSession(request);
        if (session.isEmpty()) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("user.not.logged.in.or.expired");
        }
        return ResponseDTO.ok(userAccessService.checkAccessPermission(session.get().getUsername(), path));
    }

    /**
     * 厂商 `GET /v1/user/list?page&size&keyword`：用户列表（条目是 `UserRoles`，16 键）。
     * <p>与 `/v1/user/info` 的三处不同见 `IUserRolesQueryService`；`roles` 复用 `/v1/role` 的装配。
     */
    @GetMapping("/v1/user/list")
    public ResponseDTO<com.csm.paas.core.api.dto.UserRoleListResponseDTO> userRolesList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.UserRolesAssembler.toResponse(
                userRolesQueryService.queryUserRolesPage(
                        PageUtils.normalizePage(page), PageUtils.normalizeSize(size),
                        keyword == null ? null : keyword.replaceAll(" +", ""))));
    }

    /**
     * 厂商 `GET /v1/user/import/template`：下载用户导入模板（xls）。
     * <p>响应头照抄厂商 `ResponseUtils.getAttachmentHeaderValue`：`attachment; filename*=UTF-8''<urlencode>`；
     * `Content-Type: application/vnd.ms-excel`；文件名来自 i18n `export.file.name.user.import.template`。
     */
    @GetMapping("/v1/user/import/template")
    public void usersImportTemplate(HttpServletResponse response) throws java.io.IOException {
        byte[] bytes = com.csm.paas.core.trigger.assembler.UserImportTemplateExcelWriter.build(messageResolver);
        response.reset();
        response.setContentType("application/vnd.ms-excel");
        String filename = messageResolver.resolve("export.file.name.user.import.template");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    /**
     * 厂商 `POST /v1/user/import`（multipart `file` + `isCover`）：用户导入。
     *
     * <p>三件事：① MIME 守卫（非 Excel → 400 `global.import.supported.file.type`）；
     * ② 用 POI 解析成行（空表 ⇒ 空列表 ⇒ 库无变化，仍是 200 `data:null`）；
     * ③ 交给领域服务按厂商规则写库（**不带事务**，见 {@code IUserImportService} 的文档）。
     *
     * <p>厂商控制器还打了 `@AuditLog(...)`；我们目前没有审计日志落库链路（见 `docs/deviations.md`）。
     */
    @PostMapping(value = "/v1/user/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<Object> userImport(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam("isCover") Boolean isCover)
            throws java.io.IOException {
        if (file == null || !com.csm.paas.core.trigger.assembler.ExcelFileValidator.isExcel(file.getInputStream())) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("global.import.supported.file.type");
        }
        java.util.List<com.csm.paas.core.domain.user.model.valobj.ImportUserRowVO> rows =
                com.csm.paas.core.trigger.assembler.UserImportExcelParser.parse(file.getInputStream());
        userImportService.importUsers(rows, Boolean.TRUE.equals(isCover));
        return ResponseDTO.ok(null);
    }

    /**
     * 厂商 `POST /v1/user/recovery`（multipart `file`）：用户恢复。
     *
     * <p><b>照抄一个"空实现"</b>：厂商 `handleRecoveryExcel`（`UserService.java:947-948`）**方法体为空**——
     * 这个端点只做 Excel 格式校验，然后 `Response.ok(null)`，**不写任何数据**。
     * <p>非 Excel → 400 `global.import.supported.file.type`（MIME 探测，见 `ExcelFileValidator`）。
     */
    @PostMapping(value = "/v1/user/recovery", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<Object> usersRecovery(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        if (file == null || !com.csm.paas.core.trigger.assembler.ExcelFileValidator.isExcel(file.getInputStream())) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("global.import.supported.file.type");
        }
        // 厂商 handleRecoveryExcel 是空方法 ⇒ 校验通过即返回 null，不写任何数据
        return ResponseDTO.ok(null);
    }

    /**
     * 厂商 `POST /v1/user/export`（body 是 `List<String> userIdList`，可为空）：导出用户 xls。
     * <p>与 `/v1/user/backup` **共用** `generateExportExcel`（厂商两个处理器方法体相同，文件名都用
     * i18n `export.file.name.user.export`）。
     */
    @PostMapping(value = "/v1/user/export", produces = "application/vnd.ms-excel")
    public void usersExport(@org.springframework.web.bind.annotation.RequestBody(required = false)
                            java.util.List<String> userIdList,
                            HttpServletResponse response) throws java.io.IOException {
        writeUserExcel(userIdList, response);
    }

    /** 厂商 `POST /v1/user/backup`：与 `/export` 同一实现（同一个生成函数、同一个文件名键）。 */
    @PostMapping(value = "/v1/user/backup", produces = "application/vnd.ms-excel")
    public void usersBackUp(@org.springframework.web.bind.annotation.RequestBody(required = false)
                            java.util.List<String> userIdList,
                            HttpServletResponse response) throws java.io.IOException {
        writeUserExcel(userIdList, response);
    }

    private void writeUserExcel(java.util.List<String> userIdList, HttpServletResponse response)
            throws java.io.IOException {
        byte[] bytes = com.csm.paas.core.trigger.assembler.UserExportExcelWriter.build(
                messageResolver, userExportService.exportRows(userIdList));
        response.reset();
        response.setContentType("application/vnd.ms-excel");
        String filename = messageResolver.resolve("export.file.name.user.export");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    /**
     * 厂商 `POST /v1/user/update_avatar/{userId}`（multipart `file`）：修改头像。
     * <p>`>204800`（200KiB）→ 400 `global.avatar.size.limit`；否则算 base64/md5 →
     * 域服务（算 icon + 推 WebSocket + 只更新 avatar）→ 返回**用户详情**。
     */
    @PostMapping(value = "/v1/user/update_avatar/{userId}",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<Object> updateAvatar(
            @org.springframework.web.bind.annotation.PathVariable String userId,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        if (file == null || file.getSize() > 204800L) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("global.avatar.size.limit");
        }
        byte[] bytes = file.getBytes();
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
        String md5 = org.springframework.util.DigestUtils.md5DigestAsHex(bytes);
        userAvatarService.updateAvatar(userId, base64, md5);
        return ResponseDTO.ok(userQueryService.queryDetailById(userId));
    }

    @GetMapping("/v1/user/info")
    public ResponseDTO<UserInfoListResponseDTO> userInfoList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        String normalizedKeyword = keyword == null ? null : keyword.replaceAll(" +", "");
        return ResponseDTO.ok(UserInfoAssembler.toResponse(
                userQueryService.queryUserInfoPage(normalizedPage, normalizedSize, normalizedKeyword)));
    }

    /**
     * 厂商 `GET /v1/user/{roleId}/users?page&size`：某战位下的用户列表。
     * <p>响应键是 **`userList`**（不是 `list`）；厂商**不过滤 root 的 total、却过滤列表里的 root**；
     * 厂商还会把口令哈希一起返回，我们置 null（刻意偏离）。
     */
    @GetMapping("/v1/user/{roleId}/users")
    public ResponseDTO<UserListByRoleDTO> usersByRoleId(@PathVariable String roleId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ResponseDTO.ok(UserAssembler.toResponse(userQueryService.queryUsersByRoleId(
                roleId, PageUtils.normalizePage(page), PageUtils.normalizeSize(size))));
    }

    /**
     * 厂商 `GET /v1/user/detailByUserId?userId=`：用户详情（**用户管理编辑抽屉的前置**）。
     * <p>17 个键；`roles`/`belongRole` 是 **Role 对象**、`subRoles` 是 SubRole 对象——
     * 与 `/v1/user/info` 里同名的字符串数组**不是同一个形状**（见 `docs/findings/25`）。
     * <p>不存在时抛 `user.missing.user`（厂商 `UserService.java:1753`）。**公开**（厂商不校验会话）。
     */
    @GetMapping("/v1/user/detailByUserId")
    public ResponseDTO<UserDetailDTO> userDetailByUserId(@RequestParam String userId) {
        return ResponseDTO.ok(UserDetailAssembler.toResponse(userQueryService.queryDetailById(userId)));
    }

    /**
     * 厂商 `POST /v1/user`：新增用户。
     * <p>控制器层的三道闸照抄厂商（`UserController.java:294-306`）：
     * ① 调用者**必须有 admin 战位**，否则 `role.normal.user.cannot.user` → **HTTP 500**（SystemException，不是 400）；
     * ② 用户名已存在 → 400 `user.username.already.exists`；③ 口令为空 → 400 `user.password.empty`。
     * <p>校验与派生在领域层，落库 + 关系写入在 Case 层一个事务里（**厂商 `add` 没有事务，会留半成品用户**，
     * 见 `docs/findings/30` §5）。
     */
    @PostMapping("/v1/user")
    public ResponseDTO<UserDTO> addUser(@RequestBody UserFormDTO form, HttpServletRequest request) {
        UserCallerVO caller = currentCaller(request);
        if (!caller.isAdminRole()) {
            throw new SystemException("role.normal.user.cannot.user");
        }
        String username = form.getUsername() == null ? null : form.getUsername().replaceAll(" +", "");
        if (username != null && userRepository.queryByUsername(username).isPresent()) {
            throw new WrongArgumentException("user.username.already.exists");
        }
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            throw new WrongArgumentException("user.password.empty");
        }
        UserEntity created = userAddCase.addUser(toFormVO(form));
        return ResponseDTO.ok(UserAssembler.toResponse(created));
    }

    /**
     * 厂商 `PUT /v1/user/{userId}`：修改用户。
     * <p>权限矩阵照抄厂商 `UserController.updateUser`（`UserController.java:405-428`）：
     * <ol>
     *   <li>调用者 username == `admin` → 谁都能改；</li>
     *   <li>否则调用者有 admin 战位 → **不能改 `admin` 用户**（400 `role.cannot.modify.super.op.admin.storage`），其余可改；</li>
     *   <li>否则**只能改自己**；改别人 → **HTTP 500** `role.normal.user.cannot.modify.other.user`（SystemException，不是 403）。</li>
     * </ol>
     * <p>域规则与"哪些字段能改"（含 **email/birth/phone 被强制写回原值**）见
     * {@code IUserCommandService.prepareUpdatedUser}。
     */
    @PutMapping("/v1/user/{userId}")
    public ResponseDTO<UserDTO> updateUser(@PathVariable String userId, @RequestBody UserFormDTO form,
                                          HttpServletRequest request) {
        UserCallerVO caller = currentCaller(request);
        if (!"admin".equals(caller.getUsername())) {
            java.util.Optional<UserEntity> adminUser = userRepository.queryByUsername("admin");
            if (caller.isAdminRole()) {
                if (adminUser.isPresent() && userId.equals(adminUser.get().getId())) {
                    throw new WrongArgumentException("role.cannot.modify.super.op.admin.storage");
                }
            } else {
                String callerId = userRepository.queryByUsername(caller.getUsername())
                        .map(UserEntity::getId).orElse(null);
                if (callerId == null || !callerId.equals(userId)) {
                    throw new SystemException("role.normal.user.cannot.modify.other.user");
                }
            }
        }
        UserEntity updated = userUpdateCase.updateUser(userId, toFormVO(form));
        return ResponseDTO.ok(UserAssembler.toResponse(updated));
    }

    private static UserFormVO toFormVO(UserFormDTO form) {
        return UserFormVO.builder()
                .username(form.getUsername()).nickname(form.getNickname()).password(form.getPassword())
                .status(form.getStatus()).email(form.getEmail()).birth(form.getBirth())
                .phone(form.getPhone()).desc(form.getDesc()).avatar(form.getAvatar())
                .roleListKey(form.getRoleListKey()).userListKey(form.getUserListKey())
                .subRoleListId(form.getSubRoleListId())
                .build();
    }

    /**
     * 厂商 `GET /v1/user/{userId}`：按主键查用户（**14 键**，`password` 被厂商置 null）。
     * <p>不存在 → `user.id.query.not.found`（"查询无此用户ID"）——注意与相邻的
     * `user.id.not.found`（"查无此id的用户"，删用户路径）**是两个不同的键**，都实测过。
     * <p>**公开**（厂商不校验会话）。
     */
    @GetMapping("/v1/user/{userId}")
    public ResponseDTO<UserDTO> userById(@PathVariable String userId) {
        return userRepository.queryById(userId)
                .map(u -> ResponseDTO.ok(UserAssembler.toResponse(u)))
                .orElseThrow(() -> new WrongArgumentException("user.id.query.not.found"));
    }

    /**
     * 厂商 `POST /v1/user/userToRole`：**替换用户全部**战位/角色关联（不分类型）。
     * <p>`roleIdList` 先去重；用户不存在 → 400 `user.missing.user.info`。返回 `true`。
     */
    @PostMapping("/v1/user/userToRole")
    public ResponseDTO<Boolean> userToRole(@RequestBody RoleRelationDTO form) {
        return ResponseDTO.ok(userUpdateCase.replaceUserRoles(form.getUserId(), form.getRoleIdList()));
    }

    /**
     * 厂商 `POST /v1/user/{userId}/roles`：**只替换战位（type=1）**关联。
     * <p>用户不存在 → 400 `user.not.found`；厂商返回 `Response.ok(null)` → `data:null`。
     */
    @PostMapping("/v1/user/{userId}/roles")
    public ResponseDTO<Void> relatePositions(@PathVariable String userId, @RequestBody List<String> roleIdList) {
        userUpdateCase.relatePositions(userId, roleIdList);
        return ResponseDTO.ok(null);
    }

    /**
     * 厂商 `DELETE /v1/user/{userId}`：删除用户。
     * <p>权限矩阵与"无权限时 200 但不删"的假成功见 {@code IUserCommandService} 的文档；
     * 未登录时抛 401（厂商该端点先取 `getCurrentUserInfo`）。
     */
    @DeleteMapping("/v1/user/{userId}")
    public ResponseDTO<Boolean> deleteUser(@PathVariable String userId, HttpServletRequest request) {
        userCommandService.deleteUser(userId, currentCaller(request));
        return ResponseDTO.ok(true);
    }

    /**
     * 厂商 `DELETE /v1/user/all_user?userId=a,b`：批量删除（**逐个独立事务**，不是整批原子——
     * 厂商控制器是循环调用单删接口，见 `UserController.java:390-392`）。
     */
    @DeleteMapping("/v1/user/all_user")
    public ResponseDTO<Boolean> deleteAllUser(@RequestParam String userId, HttpServletRequest request) {
        UserCallerVO caller = currentCaller(request);
        for (String id : userId.split(",")) {
            userCommandService.deleteUser(id, caller);
        }
        return ResponseDTO.ok(true);
    }

    /** 从会话解析调用者身份；`adminRole` 按厂商口径取"战位列表里是否有 admin"。 */
    private UserCallerVO currentCaller(HttpServletRequest request) {
        SessionVO session = authSupport.requireSession(request);
        java.util.List<String> roleKeys = userRepository.queryRoleKeysByUsername(session.getUsername());
        return UserCallerVO.builder()
                .username(session.getUsername())
                .adminRole(roleKeys != null && roleKeys.contains("admin"))
                .build();
    }

    /**
     * 厂商 `GET /v1/user/get`：登录页的账号下拉。**公开**——实测厂商不带 cookie 也返回 200
     * （鉴权在网关侧，见 docs/findings/14-vendor-auth-is-gateway-side.md）。
     */
    @GetMapping("/v1/user/get")
    public ResponseDTO<UserLoginListDTO> loginList() {
        return ResponseDTO.ok(UserLoginListDTO.builder()
                .list(userRepository.queryLoginList().stream()
                        .map(i -> UserLoginItemDTO.builder()
                                .username(i.getUsername())
                                .lastLoginRole(i.getLastLoginRole())
                                .avatar(i.getAvatar())
                                .build())
                        .toList())
                .total(userRepository.count())
                .build());
    }

    /**
     * 厂商 `GET /v1/user/tenx_user?userName=`：天速云用户列表（含 `apiToken`）。
     *
     * <p>厂商 `UserController.java:549-554` → `UserService.getTenxUserList:1584-1618`。
     * **照抄的怪点**：厂商 `TenxCloudImpl.getUserList:634-639` 是恒空 stub ⇒ 该端点在本厂商构建里
     * 可观测结果**恒为 `data: []`**（`userName` 过滤也只是对空列表过滤），
     * `service.get.container.admin.info.failed` 不可达。规则见 {@code ITenxUserQueryService}。
     *
     * <p>**公开**（与相邻只读端点一致，鉴权在网关侧）。
     */
    @GetMapping("/v1/user/tenx_user")
    public ResponseDTO<List<TenxUserInfoDTO>> tenxUserList(@RequestParam(required = false) String userName) {
        return ResponseDTO.ok(tenxUserQueryService.queryTenxUserList(userName).stream()
                .map(u -> TenxUserInfoDTO.builder()
                        .userName(u.getUserName())
                        .apiToken(u.getApiToken())
                        .build())
                .toList());
    }

    /**
     * 厂商 `GET /v1/user`：**当前登录态**，形状与登录响应相同（`UserLogined`，20 键，含 `role`）。
     * <p>前端 `getInitialState` 直接把它当 `currentUser`，并用 `currentUser.role.key` 判 `canAdmin`
     * （`function $T`，`umi.d08aa716.js`）——缺 `role` 会被路由守卫弹回登录页。
     */
    @GetMapping("/v1/user")
    public ResponseDTO<LoginUserDTO> currentUser(HttpServletRequest request) {
        authSupport.requireSession(request);
        String token = authSupport.readToken(request);
        return authService.currentLogin(token)
                .map(result -> ResponseDTO.ok(LoginAssembler.toResponse(result)))
                .orElseThrow(() -> new BusinessException(ResponseCode.UNAUTHENTICATED, "用户不存在"));
    }

    /**
     * `GET /v1/user/getLoginUser`：**前端指向另一个后端的绝对路径**，我们在这里补一条等价读取。
     *
     * <p>证据：厂商 gaop-be 全仓 grep 不到这个映射；前端 bundle 里它是绝对 URL
     * `http://&lt;host&gt;/zk-102-jsxx-software/zk-102-jsxx-back-8304/v1/user/getLoginUser`
     * （`p__operate__home__index.a56e7a11.async.js`：`0===n.code && n.data && Ge(n.data)`，
     * 拿到的 `data` 随后被当作 `testCode` 拼进 `/v1/thirdPart/getUnreadCount` 与 socket.io 的
     * `/message/unReadCount/&lt;code&gt;`）。也就是说那是一个**别的服务**的登录用户标识，
     * 本重写没有它的实现也没有它的库。
     *
     * <p>本端点的定位：让前端**一行不改**地把这条请求打到本服务时也有 200 与同形状的信封
     * （`{code:0,data:&lt;标量&gt;}`）。实现用厂商用户域**既有**的登录态能力
     * （{@link AuthSupport#currentSession}）：已登录 ⇒ `data` = 当前用户名（字符串，可当 `testCode` 用）；
     * 未登录 ⇒ `data` = null（前端那处 `n.data && ...` 会直接短路，不报错）。
     *
     * <p><b>这是等价读取、不是厂商原样</b>：厂商那条路径属于另一个未在 recovered 里的前端后端，
     * 无法逐字照抄；这里只保证信封与"能取到当前登录用户标识"的语义。
     */
    @GetMapping("/v1/user/getLoginUser")
    public ResponseDTO<String> getLoginUser(HttpServletRequest request) {
        return ResponseDTO.ok(authSupport.currentSession(request)
                .map(com.csm.paas.core.domain.user.model.valobj.SessionVO::getUsername)
                .orElse(null));
    }
}
