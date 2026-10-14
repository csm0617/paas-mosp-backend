package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.SubRoleListResponseDTO;
import com.csm.paas.core.api.dto.SubRoleIdsDTO;
import com.csm.paas.core.api.dto.SubRoleItemDTO;
import com.csm.paas.core.domain.subrole.service.ISubRoleCommandService;
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

    @Resource
    private ISubRoleCommandService subRoleCommandService;

    /**
     * 厂商 `GET /v1/sub_role/{id}`：子角色详情。
     *
     * <p>`nameUs`/`nameFr` 兜底成 `name`；`userList`/`roleList` 不填（null）；查不到 ⇒ `data:null`。
     * 规则见 `ISubRoleQueryService.querySubRoleDetail`（规格 `findings/147`）。
     */
    @GetMapping("/{id}")
    public ResponseDTO<SubRoleItemDTO> subRoleDetail(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        var role = subRoleQueryService.querySubRoleDetail(id);
        if (role == null) {
            return ResponseDTO.ok(null);
        }
        return ResponseDTO.ok(SubRoleItemDTO.builder()
                .id(role.getId()).key(role.getKey()).name(role.getName())
                .nameFr(role.getNameFr()).nameUs(role.getNameUs())
                .updatedAt(role.getUpdatedAt()).createdAt(role.getCreatedAt())
                .desc(role.getDesc())
                .userList(null).roleList(null)
                .build());
    }

    /**
     * 厂商 `DELETE /v1/sub_role/delete`：按 id 集合删子角色（body `{subRoleIdList}`）。
     *
     * <p>body 为空/`subRoleIdList` 为空 ⇒ **401 + "The id can't be empty"**（厂商如此）。
     * 逐个 id 删三处关联；**恒返回 true**。规则见 `ISubRoleCommandService.deleteSubRoles`（规格 `findings/147`）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/delete")
    public ResponseDTO<Boolean> deleteSubRoleById(
            @org.springframework.web.bind.annotation.RequestBody SubRoleIdsDTO form) {
        if (form == null || form.getSubRoleIdList() == null || form.getSubRoleIdList().isEmpty()) {
            return ResponseDTO.fail(401, "The id can't be empty");
        }
        return ResponseDTO.ok(subRoleCommandService.deleteSubRoles(form.getSubRoleIdList()));
    }

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
