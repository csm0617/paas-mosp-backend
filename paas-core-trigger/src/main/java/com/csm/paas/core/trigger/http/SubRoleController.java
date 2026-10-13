package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.SubRoleListResponseDTO;
import com.csm.paas.core.domain.subrole.service.ISubRoleQueryService;
import com.csm.paas.core.trigger.assembler.SubRoleListAssembler;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `/v1/sub_role` 的重写版（切片 3c）。
 * <p>本轮只做 `GET /switchRole`——它是 `/usermanage/account`（用户管理页）的**前置**：
 * 页面先拉子角色下拉，再拉用户列表（见 docs/findings/26-e2e-user-page.md）。
 * <p>**公开**：厂商该端点不做应用级校验（无 cookie 也 200）。
 */
@RestController
@RequestMapping("/v1/sub_role")
public class SubRoleController {

    @Resource
    private ISubRoleQueryService subRoleQueryService;

    @GetMapping("/switchRole")
    public ResponseDTO<SubRoleListResponseDTO> switchRoleList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String subRoleName) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        String normalizedName = subRoleName == null ? null : subRoleName.replaceAll(" +", "");
        return ResponseDTO.ok(SubRoleListAssembler.toResponse(
                subRoleQueryService.querySubRoleList(normalizedPage, normalizedSize, normalizedName)));
    }
}
