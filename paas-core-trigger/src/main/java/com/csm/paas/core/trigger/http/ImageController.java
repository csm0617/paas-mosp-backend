package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.image.service.IImageQueryService;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 厂商 `/v1/image/**`：镜像管理。数据来自 **Harbor**（外部系统）。
 *
 * <p>本轮实现 `GET /v1/image/projectList`（厂商 `K8sImageServiceImpl.projectList:107-119`）：
 * 拉 Harbor 全量项目 → 过滤 `system_containers`/`system_store` → 按 `creation_time` 字符串倒序 → 分页。
 *
 * <p><b>验证状态（如实标注）</b>：契约样本来自厂商实测（`runtime-samples/v1_image_projectList.json`，
 * 2 个项目 `library`/`gaop`）；但**本机网络到不了 Harbor**（`192.168.1.52:30002`，见
 * `findings/78`），所以**没有做在线对拉**——正确性由"照抄三步 + 用实测样本做离线回放"保证。
 */
@RestController
public class ImageController {

    @Resource
    private IImageQueryService imageQueryService;

    /** 厂商 GET /v1/image/projectList：Harbor 项目列表 `{list,total}`。 */
    @GetMapping("/v1/image/projectList")
    public ResponseDTO<Map<String, Object>> projectList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        // 键名必须是 snake_case（厂商 ProjectItem 的 @JsonProperty），见 ImageProjectAssembler 的说明
        List<Map<String, Object>> list = imageQueryService.queryProjectList(p, s, keyword).stream()
                .map(com.csm.paas.core.trigger.assembler.ImageProjectAssembler::toItem)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", imageQueryService.countProjectList(keyword));
        return ResponseDTO.ok(data);
    }
}
