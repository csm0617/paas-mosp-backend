package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.LoginRequestDTO;
import com.csm.paas.core.api.dto.LoginUserDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.user.model.entity.LoginResultEntity;
import com.csm.paas.core.domain.user.service.IAuthService;
import com.csm.paas.core.trigger.assembler.LoginAssembler;
import com.csm.paas.core.types.enums.ResponseCode;
import com.csm.paas.core.types.exception.BusinessException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证入口。厂商的 `/v1/pub/login` 与 `/v1/user/login` 是**同一个 handler**
 * （`PublicController.java:571` 直接转调 `UserController.login`），这里也一样。
 * <p>Cookie 形态照抄厂商：`Set-Cookie: gateway-token=&lt;uuid&gt;; Path=/`（无 HttpOnly/Secure/Max-Age）。
 */
@RestController
public class AuthController {

    @Resource
    private IAuthService authService;

    @Resource
    private AuthSupport authSupport;

    @PostMapping({"/v1/pub/login", "/v1/user/login"})
    public ResponseDTO<LoginUserDTO> login(@RequestBody LoginRequestDTO request, HttpServletResponse response) {
        if (request.getUsername() == null || request.getPassword() == null || request.getRole() == null) {
            throw new BusinessException(ResponseCode.ILLEGAL_PARAMETER, "用户名、密码、战位不能为空");
        }
        LoginResultEntity result = authService.login(request.getUsername(), request.getPassword(), request.getRole());
        response.addHeader("Set-Cookie", AuthSupport.COOKIE_NAME + "=" + result.getToken() + "; Path=/");
        return ResponseDTO.ok(LoginAssembler.toResponse(result));
    }

    @PostMapping({"/v1/pub/logout", "/v1/user/logout"})
    public void logout(HttpServletRequest request) {
        authService.logout(authSupport.readToken(request));
    }
}
