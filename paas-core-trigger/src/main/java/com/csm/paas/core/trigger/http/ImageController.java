package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 厂商 `/v1/image/**`：镜像管理。**这块的数据来自 Harbor（外部系统）**，属另一域（gaop-image-uploader / 镜像仓），
 * 本实现先如实返回空列表，保证前端页面能渲染（不报错）——**不是假装实现了 Harbor**。
 * <p>证据：厂商 `/v1/image/projectList` 的 data 是 Harbor 的 project 对象
 * （`creation_time/owner_name/project_id/name/repo_count`，见 runtime-samples/v1_image_projectList.json）。
 */
@RestController
public class ImageController {

    /** 厂商 GET /v1/image/projectList：Harbor 项目列表（本实现为空）。 */
    @GetMapping("/v1/image/projectList")
    public ResponseDTO<Map<String, Object>> projectList() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", List.of());
        data.put("total", 0);
        return ResponseDTO.ok(data);
    }
}
