package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.UserDTO;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;
import com.csm.paas.core.trigger.assembler.UserAssembler;
import com.csm.paas.core.types.enums.ResponseCode;
import com.csm.paas.core.types.exception.BusinessException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/v1/user")
    public ResponseDTO<UserDTO> currentUser(HttpServletRequest request) {
        SessionVO session = authSupport.requireSession(request);
        return userRepository.queryByUsername(session.getUsername())
                .map(user -> ResponseDTO.ok(UserAssembler.toResponse(user)))
                .orElseThrow(() -> new BusinessException(ResponseCode.UNAUTHENTICATED, "用户不存在"));
    }
}
