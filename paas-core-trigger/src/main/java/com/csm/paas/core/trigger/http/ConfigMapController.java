package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ConfigMapInfoDTO;
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
 * <p>已实现：`GET /`（列表）、`GET /{name}`、`GET /{name}/exist`、`GET /relationService`。
 * 未实现：`POST /`（创建）、`POST|DELETE /{name}/config`、`DELETE /?names=`、
 * `POST /backup|/import|/importListen|/listenBackup`。
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
    /**
     * 厂商 `GET /v1/config_map/relationService?name=`：配置组关联的服务。
     *
     * <p>把 data 的 **key 当 subPath**，扫描 Deployment 的卷挂载得出 `{configMapName, config[]}`；
     * ConfigMap 读不到 ⇒ 空列表。规则见 `IServiceQueryService.queryConfigMapRelations`（规格 `findings/143`）。
     */
    @GetMapping("/relationService")
    public ResponseDTO<java.util.List<ConfigMapInfoDTO>> configMapRelationService(
            @RequestParam String name) {
        return ResponseDTO.ok(serviceQueryService.queryConfigMapRelations(namespace, name).stream()
                .map(info -> ConfigMapInfoDTO.builder()
                        .configMapName(info.getConfigMapName())
                        .config(info.getConfig().stream()
                                .map(r -> ConfigMapInfoDTO.RelationServiceInfoDTO.builder()
                                        .serviceName(r.getServiceName()).mountPath(r.getMountPath())
                                        .build())
                                .toList())
                        .build())
                .toList());
    }

    /**
     * 厂商 `GET /v1/config_map/{name}/exist`：配置组是否存在（404 ⇒ `false`，其它错误 ⇒ 500）。
     */
    @GetMapping("/{name}/exist")
    public ResponseDTO<Boolean> existConfigmap(@org.springframework.web.bind.annotation.PathVariable String name) {
        return ResponseDTO.ok(serviceQueryService.configMapExists(namespace, name));
    }

    /**
     * 厂商 `GET /v1/config_map/{name}`：配置组详情。
     *
     * <p>名称为空 ⇒ 400 `configmap.name.must.not.empty`；名称不匹配 `[-._a-zA-Z0-9]+` ⇒
     * 400 `configmap.name.not.invalid`；不存在 ⇒ 400 `configmap.not.exist`（带名称）。
     */
    @GetMapping("/{name}")
    public ResponseDTO<java.util.Map<String, Object>> configmap(
            @org.springframework.web.bind.annotation.PathVariable String name) {
        return ResponseDTO.ok(serviceQueryService.queryConfigMap(namespace, name));
    }

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
