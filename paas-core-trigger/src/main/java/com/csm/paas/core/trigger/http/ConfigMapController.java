package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ConfigMapInfoDTO;
import com.csm.paas.core.api.dto.CreateConfigInConfigMapDTO;
import com.csm.paas.core.api.dto.CreateConfigMapDTO;
import com.csm.paas.core.api.dto.ConfigMapListDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `ConfigMapController`（`/v1/config_map`）——目前只实现 **`GET /`（列表）**。
 *
 * <p>已实现：`GET /`（列表）、`GET /{name}`、`GET /{name}/exist`、`GET /relationService`、
 * `POST /`（创建）、`POST /{name}/config`、`DELETE /{name}/config/{configName}`、`DELETE /?names=`。
 * 已实现：`POST /backup`、`POST /listenBackup`、`POST /import`。
 * 未实现：`POST /importListen`（它会**改 ingress Service 的端口**，风险高，另行评估）。
 */
@RestController
@RequestMapping("/v1/config_map")
@RequiredArgsConstructor
public class ConfigMapController {

    private final IServiceQueryService serviceQueryService;
    private final org.springframework.context.MessageSource messageSource;

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
     * 厂商 `POST /v1/config_map/import`（multipart）：导入配置组（JSON 是 ConfigMap 数组）。
     *
     * <p>逐条：存在 ⇒ replace、不存在 ⇒ create；**失败只记日志**；**恒返回 true**。规则见
     * `IServiceQueryService.importConfigMaps`（规格 `findings/146`）。
     *
     * <p>部件名兼容两种写法：厂商的参数名是 `json`（未注解，靠编译参数名绑定），前端用 FormData 上传；
     * 我们优先取名为 `json` 的部件，取不到则取**第一个**文件部件。
     */
    @org.springframework.web.bind.annotation.PostMapping(value = "/import",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<Boolean> importConfigMap(
            @org.springframework.web.bind.annotation.RequestPart(value = "json", required = false)
            org.springframework.web.multipart.MultipartFile json,
            jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        org.springframework.web.multipart.MultipartFile file = json;
        if (file == null && request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multi) {
            file = multi.getFileMap().values().stream().findFirst().orElse(null);
        }
        java.util.List<java.util.Map<String, Object>> configMaps = file == null
                ? java.util.List.of()
                : new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        file.getInputStream(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        });
        return ResponseDTO.ok(serviceQueryService.importConfigMaps(namespace, configMaps));
    }

    /**
     * 厂商 `POST /v1/config_map/backup`：导出配置组（**JSON 附件**，不是 JSON 信封）。
     *
     * <p>body 是**配置组名数组**；为空/不传 ⇒ 导出"不带 `ingress-lb` 标签"的全部 ConfigMap。
     * 响应头 `Content-Type: application/json` + 文件名 `配置组列表.json`。规则见
     * `IServiceQueryService.exportConfigMaps`（规格 `findings/145`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/backup")
    public void backUpConfigMap(
            @org.springframework.web.bind.annotation.RequestBody(required = false) java.util.List<String> nameList,
            HttpServletResponse response) throws java.io.IOException {
        var configMaps = serviceQueryService.exportConfigMaps(namespace, nameList);
        writeJsonAttachment(response, configMaps, "export.file.name.configmap.export");
    }

    /**
     * 厂商 `POST /v1/config_map/listenBackup`：导出 tcp/udp 监听配置（**JSON 附件**）。
     *
     * <p>响应体是 `{tcpListen, udpListen, ingressList}`，其中 `ingressList` **恒为 null**。
     * 规则见 `IServiceQueryService.exportListenConfigs`（规格 `findings/145`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/listenBackup")
    public void exportTcpAndUdp(HttpServletResponse response) throws java.io.IOException {
        var info = serviceQueryService.exportListenConfigs(namespace);
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("tcpListen", info.getTcpListen());
        body.put("udpListen", info.getUdpListen());
        body.put("ingressList", info.getIngressList());
        writeJsonAttachment(response, body, "export.file.name.listen.export");
    }

    /** 厂商 `ResponseUtils.attachment` + Jackson 写流：`application/json` + 附件名（走 i18n）。 */
    private void writeJsonAttachment(HttpServletResponse response, Object body, String filenameKey)
            throws java.io.IOException {
        response.reset();
        response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(messageSource.getMessage(filenameKey, null, java.util.Locale.getDefault()),
                        java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(body));
        response.flushBuffer();
    }

    /**
     * 厂商 `POST /v1/config_map`：建配置组（body 只有 `name`）。
     *
     * <p>名称空白/非法 ⇒ 两个 400；**已存在 ⇒ 400 `configmap.existed`**；建失败 ⇒ 500。规则见
     * `IServiceQueryService.createConfigMap`（规格 `findings/144`）。
     */
    @org.springframework.web.bind.annotation.PostMapping
    public ResponseDTO<java.util.Map<String, Object>> createConfigMap(
            @org.springframework.web.bind.annotation.RequestBody CreateConfigMapDTO form) {
        return ResponseDTO.ok(serviceQueryService.createConfigMap(namespace, form == null ? null : form.getName()));
    }

    /**
     * 厂商 `POST /v1/config_map/{name}/config`：新增/覆盖配置组里的一个配置项（body `{name, value}`）。
     *
     * <p>`value` 为空 ⇒ 存**空串**；同名**覆盖**。规则见
     * `IServiceQueryService.upsertConfigEntry`（规格 `findings/144`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/{name}/config")
    public ResponseDTO<java.util.Map<String, Object>> createConfig(
            @org.springframework.web.bind.annotation.PathVariable String name,
            @org.springframework.web.bind.annotation.RequestBody CreateConfigInConfigMapDTO form) {
        return ResponseDTO.ok(serviceQueryService.upsertConfigEntry(namespace, name,
                form == null ? null : form.getName(), form == null ? null : form.getValue()));
    }

    /**
     * 厂商 `DELETE /v1/config_map/{name}/config/{configName}`：删一个配置项
     * （配置项不存在 ⇒ 400 `configmap.name.not.exist`）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{name}/config/{configName}")
    public ResponseDTO<java.util.Map<String, Object>> deleteConfig(
            @org.springframework.web.bind.annotation.PathVariable String name,
            @org.springframework.web.bind.annotation.PathVariable String configName) {
        return ResponseDTO.ok(serviceQueryService.deleteConfigEntry(namespace, name, configName));
    }

    /**
     * 厂商 `DELETE /v1/config_map?names=`：删配置组（`names` 逗号分隔；删除失败只记日志）。
     *
     * <p>返回的是**成功删除的那些名字**，且用的是**未 trim 的原值**。规则见
     * `IServiceQueryService.deleteConfigMaps`（规格 `findings/144`）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping
    public ResponseDTO<java.util.List<String>> deleteConfigMap(@RequestParam String names) {
        return ResponseDTO.ok(serviceQueryService.deleteConfigMaps(namespace, names));
    }

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
