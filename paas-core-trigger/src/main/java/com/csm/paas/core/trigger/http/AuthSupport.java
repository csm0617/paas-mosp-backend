package com.csm.paas.core.trigger.http;

import com.csm.paas.core.domain.user.model.valobj.SessionVO;
import com.csm.paas.core.domain.user.service.IAuthService;
import com.csm.paas.core.types.exception.UnauthorizedException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 读取并校验 `gateway-token` cookie。
 * <p>厂商侧没有全局拦截器，是**各端点自己调 `getCurrentUser`**（docs/findings/12-auth-contract.md），
 * 这里提供同一个语义的共用件，受保护端点显式调用。
 */
@Component
public class AuthSupport {

    public static final String COOKIE_NAME = "gateway-token";

    @Resource
    private IAuthService authService;

    /** 取当前会话；未登录抛 {@link UnauthorizedException}。 */
    public SessionVO requireSession(HttpServletRequest request) {
        return currentSession(request).orElseThrow(UnauthorizedException::new);
    }

    public java.util.Optional<SessionVO> currentSession(HttpServletRequest request) {
        String token = readToken(request);
        return authService.currentSession(token);
    }

    public String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
