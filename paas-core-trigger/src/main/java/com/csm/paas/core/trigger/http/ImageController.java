package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.image.service.IImageQueryService;
import com.csm.paas.core.trigger.assembler.ImageProjectAssembler;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 厂商 `/v1/image/**`：镜像管理。
 *
 * <p><b>数据源是数据库 `g_group`</b>（不是 Harbor）——厂商 `getImageServiceInstance()` 无条件返回 k3s 实现，
 * 见 `docs/findings/79`。
 *
 * <p>`GET /v1/image/projectList` 的规则见 `IImageQueryService.queryProjectList`；
 * **注意它在表为空时会写库**（创建 3 个内置项目），这是厂商行为。
 */
@RestController
public class ImageController {

    @Resource
    private IImageQueryService imageQueryService;

    /** 厂商 GET /v1/image/projectList：项目列表 `{list,total}`（键名 snake_case，见 `ImageProjectAssembler`）。 */
    @GetMapping("/v1/image/projectList")
    public ResponseDTO<Map<String, Object>> projectList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        var result = imageQueryService.queryProjectList(
                PageUtils.normalizePage(page), PageUtils.normalizeSize(size), keyword);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", result.getList().stream().map(ImageProjectAssembler::toItem).toList());
        data.put("total", result.getTotal());
        return ResponseDTO.ok(data);
    }
}
