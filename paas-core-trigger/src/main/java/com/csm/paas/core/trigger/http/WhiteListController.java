package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.WhiteListDTO;
import com.csm.paas.core.api.dto.WhiteListInfoDTO;
import com.csm.paas.core.domain.whitelist.model.entity.WhiteListEntity;
import com.csm.paas.core.domain.whitelist.service.IWhiteListService;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 厂商 `WhiteListController`（`/v1/white_list`）的重写版。
 *
 * <p>三个端点：`GET /list`、`POST ?path=`、`DELETE ?pathId=a,b`。
 * 规则见 {@link IWhiteListService}（规格 `findings/159`）。
 */
@RestController
@RequestMapping("/v1/white_list")
public class WhiteListController {

    @Resource
    private IWhiteListService whiteListService;

    /** 厂商 `GET /v1/white_list/list?page&size&keyword`：`path LIKE %keyword%` + `created_at DESC`。 */
    @GetMapping("/list")
    public ResponseDTO<WhiteListInfoDTO> whiteList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        var result = whiteListService.list(PageUtils.normalizePage(page), PageUtils.normalizeSize(size), keyword);
        return ResponseDTO.ok(WhiteListInfoDTO.builder()
                .list(result.getList().stream().map(WhiteListController::toDTO).toList())
                .total(result.getTotal())
                .build());
    }

    /** 厂商 `POST /v1/white_list?path=`：新增放行路径（已存在 ⇒ 400 `software.path.already.exists`）。 */
    @PostMapping
    public ResponseDTO<WhiteListDTO> addPath(@RequestParam String path) {
        WhiteListEntity added = whiteListService.addPath(path);
        return ResponseDTO.ok(added == null ? null : toDTO(added));
    }

    /** 厂商 `DELETE /v1/white_list?pathId=1,2`：批量删除（逐个 id，不存在 ⇒ 400）。恒返回 `true`。 */
    @DeleteMapping
    public ResponseDTO<Boolean> deletePath(@RequestParam String pathId) {
        List<Long> ids = Arrays.stream(pathId.split(",")).map(s -> Long.parseLong(s.trim())).toList();
        whiteListService.deletePaths(ids);
        return ResponseDTO.ok(true);
    }

    private static WhiteListDTO toDTO(WhiteListEntity e) {
        return WhiteListDTO.builder()
                .id(e.getId()).path(e.getPath())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}
