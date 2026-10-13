package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.RoleListResponseDTO;
import com.csm.paas.core.api.dto.RoleSoftwareDTO;
import com.csm.paas.core.api.dto.RoleSoftwareListDTO;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.trigger.assembler.RoleListAssembler;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `/v1/role` 与 `/v1/role/list` 的重写版（战位读切片）。
 * <p>两者在厂商侧是同一份数据的不同入口（`RoleController.java:69` 与 `:88`），这里共用一条查询。
 * <p>原型未实现分页参数与 i18n 覆盖层（见工单 10/07）。
 */
@RestController
@RequestMapping("/v1/role")
public class RoleController {

    @Resource
    private IRoleQueryService roleQueryService;

    @Resource
    private AuthSupport authSupport;

    @GetMapping
    public ResponseDTO<RoleListResponseDTO> list(HttpServletRequest request) {
        // 厂商在这两个端点**不做**应用级校验（实测无 cookie 也 200），
        // 鉴权由 gateway-auth 在边缘完成，见 docs/findings/14-vendor-auth-is-gateway-side.md

        return ResponseDTO.ok(RoleListAssembler.toResponse(roleQueryService.queryRoleList()));
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

    @GetMapping("/list")
    public ResponseDTO<RoleListResponseDTO> listAll(HttpServletRequest request) {
        // 厂商在这两个端点**不做**应用级校验（实测无 cookie 也 200），
        // 鉴权由 gateway-auth 在边缘完成，见 docs/findings/14-vendor-auth-is-gateway-side.md

        return ResponseDTO.ok(RoleListAssembler.toResponse(roleQueryService.queryRoleList()));
    }
}
