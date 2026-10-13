package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.RoleListResponseDTO;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.trigger.assembler.RoleListAssembler;
import jakarta.annotation.Resource;
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

    @GetMapping
    public ResponseDTO<RoleListResponseDTO> list() {
        return ResponseDTO.ok(RoleListAssembler.toResponse(roleQueryService.queryRoleList()));
    }

    @GetMapping("/list")
    public ResponseDTO<RoleListResponseDTO> listAll() {
        return ResponseDTO.ok(RoleListAssembler.toResponse(roleQueryService.queryRoleList()));
    }
}
