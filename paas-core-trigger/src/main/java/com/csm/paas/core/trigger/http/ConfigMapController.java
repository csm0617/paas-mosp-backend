package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ConfigMapListDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `ConfigMapController`（`/v1/config_map`）——目前只实现 **`GET /`（列表）**。
 *
 * <p>未实现：`POST /`（创建）、`GET /{name}`、`GET /{name}/exist`、`POST|DELETE /{name}/config`、
 * `DELETE /?names=`、`GET /relationService`、`POST /backup|/import|/importListen|/listenBackup`。
 */
@RestController
@RequestMapping("/v1/config_map")
@RequiredArgsConstructor
public class ConfigMapController {

    private final IServiceQueryService serviceQueryService;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    /**
     * 厂商 `GET /v1/config_map?page=&size=&keyword=`：配置组列表。
     *
     * <p>标签选择器 `!ingress-lb`（"该标签不存在"）；硬编码排除 `ingress-nginx-controller` 与
     * `kube-root-ca.crt`；`keyword` 先只去空格再按正则匹配名字，**匹配为空 ⇒ `items` 为 null**；
     * **`total` 恒为关键字过滤之前的数量**。规则见
     * `IServiceQueryService.queryConfigMapList`（规格 `findings/142`）。
     */
    @GetMapping
    public ResponseDTO<ConfigMapListDTO> listConfigMap(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        var result = serviceQueryService.queryConfigMapList(namespace,
                PageUtils.normalizePage(page), PageUtils.normalizeSize(size), keyword);
        return ResponseDTO.ok(ConfigMapListDTO.builder()
                .apiVersion(result.getApiVersion()).items(result.getItems()).kind(result.getKind())
                .metadata(result.getMetadata()).total(result.getTotal())
                .build());
    }
}
