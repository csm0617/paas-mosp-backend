package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.LoginUserDTO;
import com.csm.paas.core.api.dto.UserDTO;
import com.csm.paas.core.api.dto.UserInfoListResponseDTO;
import com.csm.paas.core.api.dto.UserLoginItemDTO;
import com.csm.paas.core.api.dto.UserLoginListDTO;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;
import com.csm.paas.core.domain.user.model.valobj.UserCallerVO;
import com.csm.paas.core.domain.user.service.IAuthService;
import com.csm.paas.core.domain.user.service.IUserCommandService;
import com.csm.paas.core.domain.user.service.IUserQueryService;
import com.csm.paas.core.trigger.assembler.LoginAssembler;
import com.csm.paas.core.trigger.assembler.UserAssembler;
import com.csm.paas.core.trigger.assembler.UserInfoAssembler;
import com.csm.paas.core.types.enums.ResponseCode;
import com.csm.paas.core.types.exception.BusinessException;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private IUserRepository userRepository;

    @Resource
    private IAuthService authService;

    @Resource
    private IUserQueryService userQueryService;

    @Resource
    private IUserCommandService userCommandService;

    /**
     * 厂商 `GET /v1/user/info`：用户管理列表（用户 + 关联战位 + 关联子角色）。
     * <p>**公开**：实测厂商不带 cookie 也 200（鉴权在网关侧，见 `docs/findings/14-vendor-auth-is-gateway-side.md`）。
     * <p>分页归一化照抄厂商 `SqlUtils`；关键字先去掉空格（厂商 `UserController.java:576`）。
     */
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
}
