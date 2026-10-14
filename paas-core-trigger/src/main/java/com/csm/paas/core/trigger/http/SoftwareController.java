package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.SoftwareListDTO;
import com.csm.paas.core.domain.software.service.ISoftwareQueryService;
import com.csm.paas.core.trigger.assembler.SoftwareListAssembler;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `/v1/software` 的重写版（**本轮只做列表**）。
 *
 * <p>契约（`SoftwareController.java:142-150`）：`page`/`size` 归一化照旧；`name` 长度 &gt;36 直接 400
 * `software.search.length.invalid`（注意**不在服务端去空格**）；`order` 默认 `key`、`sort` 默认 `asc`。
 *
 * <p>这是一个**聚合端点**而不是"取表"：每行还要算 `releaseStatus`（白名单）、`web`（K8s Deployment 现场算）、
 * `url`（CS 类型拼 `gaop://cs?...`，其中 `host` 取自请求头 `x-forwarded-host`）、`roleList`（关联战位）。
 * 详见 `docs/findings/36`。
 */
@RestController
@RequestMapping("/v1/software")
public class SoftwareController {

    /** 厂商把上游代理写进这个头；CS 类型的入口地址要用它。 */
    private static final String FORWARDED_HOST_HEADER = "x-forwarded-host";

    @Resource
    private ISoftwareQueryService softwareQueryService;

    @Resource
    private com.csm.paas.core.cases.software.ISoftwareWriteCase softwareWriteCase;

    @Resource
    private com.csm.paas.core.domain.software.service.ISoftwareCommandService softwareCommandService;

    @Resource
    private com.csm.paas.core.domain.software.service.ISoftwareDetailService softwareDetailService;

    /** `relationService` 复用服务域的读能力（厂商那边也是 `SoftwareController` 调 `softwareService`）。 */
    @Resource
    private com.csm.paas.core.domain.software.service.IServiceQueryService serviceQueryService;

    /** 软件启停与状态（`GET /{id}/start|stop|status`）。 */
    @Resource
    private com.csm.paas.core.domain.software.service.ISoftwareLifecycleService softwareLifecycleService;

    @org.springframework.beans.factory.annotation.Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    /**
     * 厂商 `POST /v1/software/servicePort`：更新"软件配置里的服务端口"（body 是 `[{containerPort, serviceName}]`）。
     *
     * <p>`serviceName` 取第一条；按 `g_software_app.app_name` 找软件（一个都没有就直接 true）；
     * 每个软件**先删光**该服务下的端口映射再按 `containerPort` 逐行插入，并**保留旧的 web 入口标记**。
     * 规则见 `IServiceQueryService.updateSoftwareServicePorts`（规格 `findings/136`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/servicePort")
    public ResponseDTO<Boolean> servicePort(
            @org.springframework.web.bind.annotation.RequestBody java.util.List<com.csm.paas.core.api.dto.ServicePortFormDTO> form) {
        return ResponseDTO.ok(serviceQueryService.updateSoftwareServicePorts(form == null ? null
                : form.stream()
                .map(f -> com.csm.paas.core.domain.software.model.valobj.ServicePortItemVO.builder()
                        .containerPort(f.getContainerPort()).serviceName(f.getServiceName()).build())
                .toList()));
    }

    /**
     * 厂商 `PUT /v1/software/softwareRelateRole?softwareId=`：修改软件关联的战位（body 是 **roleKey 数组**）。
     *
     * <p>先去重、再**删光该软件已有战位关联（每删一条推一次 `/topic/software`）**、
     * 最后按 roleKey 解析成 roleId 补关联（查不到的 key 跳过）。规则见
     * `ISoftwareCommandService.updateSoftwareRelateRole`（规格 `findings/135`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/softwareRelateRole")
    public ResponseDTO<Boolean> softwareRelateRole(
            @org.springframework.web.bind.annotation.RequestParam String softwareId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) java.util.List<String> roleKeyList) {
        return ResponseDTO.ok(softwareCommandService.updateSoftwareRelateRole(softwareId, roleKeyList));
    }

    /**
     * 厂商 `POST /v1/software/exportConfigmap`：导出配置组（响应是 **JSON 附件**，不是 JSON 信封）。
     *
     * <p>收集逻辑与 `existConfigmap` 相同，但 service 分支里 **Deployment 读不到 ⇒ 400**
     * `service.get.service.config.identifier.exception`；随后按名读 ConfigMap 并把列表写成
     * `Content-Type: application/octet-stream` + `configmap.json` 附件。
     * 规则见 `IServiceQueryService.collectConfigMapsForExport`（规格 `findings/134`）。
     */
    @org.springframework.web.bind.annotation.PostMapping(value = "/exportConfigmap",
            produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void exportConfigmap(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ExportConfigDTO form,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        var configMaps = serviceQueryService.collectConfigMapsForExport(namespace,
                form == null ? null : form.getType(), form == null ? null : form.getKey());
        byte[] bytes;
        try {
            bytes = com.csm.paas.core.trigger.assembler.ExportConfigMapWriter.build(configMaps);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 厂商：写 JSON 失败 ⇒ 400 `sync.get.config.group.data.exception`
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "sync.get.config.group.data.exception");
        }
        response.reset();
        response.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode("configmap.json",
                        java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    /**
     * 厂商 `POST /v1/software/existConfigmap`：检测软件或服务是否存在 ConfigMap 配置组。
     *
     * <p>`type=software` 时 `key` 是软件 id（取其关联服务）、`type=service` 时 `key` 是 Deployment 名；
     * **只要有一个 key 收集到 configMap 名就返回 true**。规则见
     * `IServiceQueryService.existConfigMap`（规格 `findings/133`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/existConfigmap")
    public ResponseDTO<Boolean> existConfigmap(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ExportConfigDTO form) {
        return ResponseDTO.ok(serviceQueryService.existConfigMap(namespace,
                form == null ? null : form.getType(), form == null ? null : form.getKey()));
    }

    /**
     * 厂商 `POST /v1/software/image_base64`（multipart `file`）：把上传的图片转成 base64 字符串。
     *
     * <p>格式守卫照抄厂商 `imageToBase64Str:2543-2554`：Tika 探测 MIME，只有
     * `image/(jpg|jpeg|png|bmp|webp)`（含大写变体）才通过，否则 400 `global.image.format.error`。
     * 返回的是**裸 base64**（不带 `data:` 前缀）。
     */
    @org.springframework.web.bind.annotation.PostMapping(value = "/image_base64",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<String> imageToBase64(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        if (file == null
                || !com.csm.paas.core.trigger.assembler.ImageBase64Encoder.isImage(file.getInputStream())) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("global.image.format.error");
        }
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.ImageBase64Encoder.encode(file.getBytes()));
    }

    /**
     * 厂商 `PUT /v1/software/order/{orderNum}`：软件置顶配置（body `{softwareIds}`）。
     *
     * <p>逐个把 `order_top` 设成路径上的 `orderNum`、`order_time` 设成当前毫秒；
     * 某个 id 不存在 ⇒ 400 `software.id.not.found`（带该 id）；列表为空 ⇒ `data:false`。
     * 规则见 `ISoftwareCommandService.orderSoftware`（规格 `findings/131`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/order/{orderNum}")
    public ResponseDTO<Boolean> orderSoftware(
            @org.springframework.web.bind.annotation.PathVariable Integer orderNum,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            com.csm.paas.core.api.dto.SoftwareIdFormDTO form) {
        return ResponseDTO.ok(softwareCommandService.orderSoftware(
                form == null ? null : form.getSoftwareIds(), orderNum));
    }

    /**
     * 厂商 `PUT /v1/software/update_icon?softwareId=`：修改软件图标。
     *
     * <p>`icon` 与库里相同 ⇒ 不写库直接返回；`icon` 为 **null** ⇒ **清空**；软件不存在 ⇒ 400 `software.not.found`。
     * 规则见 `ISoftwareCommandService.updateIcon`（规格 `findings/130`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/update_icon")
    public ResponseDTO<String> updateIcon(
            @org.springframework.web.bind.annotation.RequestParam String softwareId,
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.SoftwareIconDTO icon) {
        return ResponseDTO.ok(softwareCommandService.updateIcon(softwareId, icon == null ? null : icon.getIcon()));
    }

    /**
     * 厂商 `PUT /v1/software/update_screenshot?softwareId=`：修改软件截图（body 是**字符串数组**）。
     *
     * <p>列表用 `,` 连接成一列；与库里相同 ⇒ 不写库；空列表 ⇒ **清空**。规则见
     * `ISoftwareCommandService.updateScreenshot`（规格 `findings/130`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/update_screenshot")
    public ResponseDTO<java.util.List<String>> updateScreenshot(
            @org.springframework.web.bind.annotation.RequestParam String softwareId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) java.util.List<String> screenShotList) {
        return ResponseDTO.ok(softwareCommandService.updateScreenshot(softwareId, screenShotList));
    }

    /**
     * 厂商 `POST /v1/software/overView`：软件总览（三组计数 `running/stop/pending`）。
     *
     * <p>三组口径不同：`software` 按"该软件关联服务的状态汇总"（关联服务找不到算 STOP，部分运行归 pending）、
     * `service` 按标签过滤后的工作负载状态、`pod` 按 pod phase。规则见
     * `IServiceQueryService.querySoftwareOverview`（规格 `findings/129`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/overView")
    public ResponseDTO<com.csm.paas.core.api.dto.OverviewInfoDTO> overView() {
        var overview = serviceQueryService.querySoftwareOverview(namespace);
        return ResponseDTO.ok(com.csm.paas.core.api.dto.OverviewInfoDTO.builder()
                .software(toStatusDTO(overview.getSoftware()))
                .service(toStatusDTO(overview.getService()))
                .pod(toStatusDTO(overview.getPod()))
                .build());
    }

    private static com.csm.paas.core.api.dto.OverviewInfoDTO.StatusInfoDTO toStatusDTO(
            com.csm.paas.core.domain.software.model.entity.SoftwareOverviewEntity.StatusInfo status) {
        return com.csm.paas.core.api.dto.OverviewInfoDTO.StatusInfoDTO.builder()
                .running(status.getRunning()).stop(status.getStop()).pending(status.getPending())
                .build();
    }

    /**
     * 厂商 `GET /v1/software/relationService/{id}`：软件关联的服务列表（界面显示，单独接口请求）。
     *
     * <p>与 `GET /v1/service` 同一个 `ServiceItemDTO` 形状，但 `mappingInfo` **真的填**
     * （`intranetInfo`/`nodePortInfo` 是数组、`ingressInfo` 恒空）；规则见
     * `IServiceQueryService.querySoftwareRelationServices`（规格 `findings/120`）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/relationService/{id}")
    public ResponseDTO<java.util.List<com.csm.paas.core.api.dto.ServiceItemDTO>> softwareRelationService(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.ServiceListAssembler.toRelationItems(
                serviceQueryService.querySoftwareRelationServices(namespace, id)));
    }

    /**
     * 厂商 `GET /v1/software/{id}`：软件详情（23 键聚合）。
     * <p>不存在 → 400 `software.software.id.not.found`（注意与删除用的 `software.not.exist.by.id` **不是同一个键**）。
     * <p>`licence` 目前只实现"未激活"分支；`framework=BS` 的 `serviceList` 未实现（回到字段默认值）——
     * 理由见 `ISoftwareDetailService` 的 javadoc。
     */
    @org.springframework.web.bind.annotation.GetMapping("/{id}")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareInfoDTO> softwareDetail(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.SoftwareInfoAssembler.toResponse(
                softwareDetailService.queryDetail(id)));
    }

    /**
     * 厂商 `POST /v1/software/{id}/service_entry?serviceId&port&type`：设置/取消软件的服务入口。
     *
     * <p>四个参数里 `serviceId` **实际是服务名**（照抄厂商的命名混乱）；`type` 1 设置、0 取消。
     * 四道"静默返回"闸（软件不存在 / 未绑定该服务 / K8s 无此服务 / 服务数据为空）都不报错、照样 200。
     * 响应是**软件详情**（23 键），规则见 {@link com.csm.paas.core.domain.software.service.IServiceQueryService#setServiceEntry}。
     */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/service_entry")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareInfoDTO> setServiceEntry(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestParam String serviceId,
            @org.springframework.web.bind.annotation.RequestParam Integer port,
            @org.springframework.web.bind.annotation.RequestParam Integer type) {
        serviceQueryService.setServiceEntry(namespace, id, serviceId, port, type);
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.SoftwareInfoAssembler.toResponse(
                softwareDetailService.queryDetail(id)));
    }

    /**
     * 厂商 `POST /v1/software/reset_service`：重置服务配置（厂商 `AppService.resetService:539-570`）。
     *
     * <p>把 gaop-namespace 里**除 `system/appName=gaop` 之外**（以及带 `ingress-lb` 标签之外）的
     * Deployment **全部删除**，返回 `data:true`；K8s 调用异常 ⇒ 400 `service.reset.failed`。
     * <p><b>破坏性操作</b>，照抄；厂商开头那段 `allPageApps` + `removeApp` 走的是时速云 App 概念，
     * 本环境没有对应物（见 findings/79），只做 Deployment 删除这一半。
     */
    @org.springframework.web.bind.annotation.PostMapping("/reset_service")
    public ResponseDTO<Boolean> resetService() {
        return ResponseDTO.ok(serviceQueryService.resetService(namespace));
    }

    /**
     * 厂商 `POST /v1/software/top?type&namespace`：容器资源使用 Top5。
     *
     * <p>`type` 取 `cpu`/`mem`/`netout`/`netin`/`diskread`/`diskwrite`（其它值 ⇒ 空数组）；
     * `namespace` 为空 ⇒ 用后端配置的命名空间。每条恒为 8 个 String 键、查不到的项是 `"0"`。
     * 规则见 {@link com.csm.paas.core.domain.software.service.IServiceQueryService#queryTopPodResources}
     * （**类 C**：需要 Prometheus / metrics-server）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/top")
    public ResponseDTO<java.util.List<com.csm.paas.core.api.dto.TopPodResourceInfoDTO>> top(
            @org.springframework.web.bind.annotation.RequestParam String type,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String namespace) {
        return ResponseDTO.ok(serviceQueryService.queryTopPodResources(type, namespace).stream()
                .map(v -> com.csm.paas.core.api.dto.TopPodResourceInfoDTO.builder()
                        .podName(v.getPodName())
                        .cpuUsageRate(v.getCpuUsageRate())
                        .memory(v.getMemory())
                        .networkOutBound(v.getNetworkOutBound())
                        .networkInBound(v.getNetworkInBound())
                        .diskRead(v.getDiskRead())
                        .diskWrite(v.getDiskWrite())
                        .namespace(v.getNamespace())
                        .build())
                .toList());
    }

    /**
     * 厂商 `PUT /v1/software/{softwareId}/service/{serviceName}?port&path`：设置端口映射路径。
     * <p>四个参数校验 + 两条业务校验（路径已占用 / 端口不存在）见 `ISoftwareQueryService.updateServicePortPath`；
     * 成功返回**重新查出来的那一行**（8 键 `ServicePort`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/{softwareId}/service/{serviceName}")
    public ResponseDTO<com.csm.paas.core.api.dto.ServicePortDTO> updateSoftwareServicePort(
            @org.springframework.web.bind.annotation.PathVariable String softwareId,
            @org.springframework.web.bind.annotation.PathVariable String serviceName,
            @org.springframework.web.bind.annotation.RequestParam int port,
            @org.springframework.web.bind.annotation.RequestParam String path) {
        var v = softwareQueryService.updateServicePortPath(softwareId, serviceName, port, path);
        return ResponseDTO.ok(com.csm.paas.core.api.dto.ServicePortDTO.builder()
                .id(v.getId()).softwareId(v.getSoftwareId()).serviceName(v.getServiceName())
                .port(v.getPort()).pathName(v.getPathName()).webEntry(v.getWebEntry())
                .allow(v.getAllow()).updatedAt(v.getUpdatedAt())
                .build());
    }

    /**
     * 厂商 `GET /v1/software/{softwareId}/service/{serviceName}`：软件在某服务下的**端口映射列表**。
     * <p>`software_id` + `service_name` 等值查询、**不排序**；查不到返回**空数组**（不是 404/400）。
     */
    @GetMapping("/{softwareId}/service/{serviceName}")
    public ResponseDTO<java.util.List<com.csm.paas.core.api.dto.ServicePortDTO>> softwareServicePorts(
            @org.springframework.web.bind.annotation.PathVariable String softwareId,
            @org.springframework.web.bind.annotation.PathVariable String serviceName) {
        return ResponseDTO.ok(softwareQueryService.queryServicePorts(softwareId, serviceName).stream()
                .map(v -> com.csm.paas.core.api.dto.ServicePortDTO.builder()
                        .id(v.getId()).softwareId(v.getSoftwareId()).serviceName(v.getServiceName())
                        .port(v.getPort()).pathName(v.getPathName()).webEntry(v.getWebEntry())
                        .allow(v.getAllow()).updatedAt(v.getUpdatedAt())
                        .build())
                .toList());
    }

    /**
     * 厂商 `POST /v1/software/software/active`：批量激活软件（请求体 `{authToken}`）。
     * <p>规则与异常键见 `ISoftwareQueryService.activateSoftware`。
     */
    @org.springframework.web.bind.annotation.PostMapping("/software/active")
    public ResponseDTO<java.util.List<com.csm.paas.core.api.dto.SoftwareAuthDTO>> activate(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ActiveCodeDTO form) {
        return ResponseDTO.ok(softwareQueryService.activateSoftware(form.getAuthToken()).stream()
                .map(v -> com.csm.paas.core.api.dto.SoftwareAuthDTO.builder()
                        .softwareKey(v.getSoftwareKey()).result(v.isResult()).build())
                .toList());
    }

    /**
     * 厂商 `POST /v1/software/cancelAuth`：批量取消软件授权。
     * <p>请求体是**字符串数组**（软件 id）；返回 `[{softwareKey, result:false}]`。
     * 规则见 `ISoftwareQueryService.cancelSoftwareAuth`（含"一个都没取消到 → 400"）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/cancelAuth")
    public ResponseDTO<java.util.List<com.csm.paas.core.api.dto.SoftwareAuthDTO>> cancelAuth(
            @org.springframework.web.bind.annotation.RequestBody java.util.List<String> softwareIdList) {
        return ResponseDTO.ok(softwareQueryService.cancelSoftwareAuth(softwareIdList).stream()
                .map(v -> com.csm.paas.core.api.dto.SoftwareAuthDTO.builder()
                        .softwareKey(v.getSoftwareKey()).result(v.isResult()).build())
                .toList());
    }

    /**
     * 厂商 `POST /v1/software/softwareCode`：生成软件申请码（`Base64(JSON({serviceIdList,machineCode}))`）。
     * <p>`softwareKeyList` 为空 → 400 `software.invalid.api.parameter`。
     */
    @org.springframework.web.bind.annotation.PostMapping("/softwareCode")
    public ResponseDTO<String> softwareCode(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.SoftwareKeyFormDTO form) {
        var identifier = softwareQueryService.generateSoftwareIdentifier(form.getSoftwareKeyList());
        // 厂商：Jackson 序列化 Identifier 后整体 Base64。字段顺序就是契约（serviceIdList → machineCode），
        // 这里用显式 ObjectNode 固定顺序，不依赖任何注解/反射顺序。
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.createObjectNode();
        var arr = node.putArray("serviceIdList");
        identifier.getServiceIdList().forEach(arr::add);
        node.put("machineCode", identifier.getMachineCode());
        try {
            String json = mapper.writeValueAsString(node);
            return ResponseDTO.ok(java.util.Base64.getEncoder()
                    .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.csm.paas.core.types.exception.SystemException("json.identifier.to.json.failed");
        }
    }

    /**
     * 厂商 `GET /v1/software/auth?page&size&name&type`：软件授权列表（`{list,total}`）。
     * <p>`name` 长度 > 36 → 400 `software.search.length.invalid`；`status` 三态见
     * `ISoftwareQueryService.queryAuthList`；排序**只按 `created_at DESC`**。
     */
    @GetMapping("/auth")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareAuthListDTO> softwareAuthList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type) {
        if (name != null && name.length() > 36) {
            throw new WrongArgumentException("software.search.length.invalid");
        }
        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        return ResponseDTO.ok(com.csm.paas.core.api.dto.SoftwareAuthListDTO.builder()
                .list(softwareQueryService.queryAuthList(p, s, name, type).stream()
                        .map(v -> com.csm.paas.core.api.dto.SoftwareAuthInfoDTO.builder()
                                .id(v.getId()).softWareName(v.getSoftWareName()).softWareKey(v.getSoftWareKey())
                                .framework(v.getFramework()).status(v.getStatus())
                                .authCreateTime(v.getAuthCreateTime()).authExpireTime(v.getAuthExpireTime())
                                .build())
                        .toList())
                .total(softwareQueryService.countAuthList(name, type))
                .build());
    }

    /**
     * 厂商 `PUT /v1/software/{id}`：修改软件。
     * <p>三道闸照抄 `SoftwareController.java:365-378`：不存在 → 400 `software.not.exist.by.id`；
     * key 与他人重复 → 400 `software.key.identifier.existed`；**name 与他人重复用的是同一个键**（厂商照抄点）。
     * <p>返回的是 23 键的 `SoftwareInfo`（与 `GET /v1/software/{id}` 同形），且 `nameUs`/`nameFr` 取自表单。
     */
    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareInfoDTO> editSoftware(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.SoftwareFormDTO form) {
        var vo = com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO.builder()
                .key(form.getKey()).name(form.getName()).nameFr(form.getNameFr()).nameUs(form.getNameUs())
                .type(form.getType()).routerKey(form.getRouterKey())
                .desc(form.getDesc()).location(form.getLocation())
                .remoteIp(form.getRemoteIp()).remoteName(form.getRemoteName()).remotePassword(form.getRemotePassword())
                .framework(form.getFramework()).execName(form.getExecName()).icon(form.getIcon())
                .screenShotList(form.getScreenShotList()).roleListKey(form.getRoleListKey())
                .build();
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.SoftwareInfoAssembler.toResponse(
                softwareWriteCase.editSoftware(id, vo)));
    }

    /**
     * 厂商 `DELETE /v1/software/{id}`：删除软件（含 4 张表的顺序删除，**整体一个事务**）。
     * <p>不存在 → 400 `software.not.exist.by.id`；厂商返回 `Response.ok(null)` → `data:null`。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ResponseDTO<Void> removeSoftware(@org.springframework.web.bind.annotation.PathVariable String id) {
        softwareWriteCase.removeSoftware(id);
        return ResponseDTO.ok(null);
    }

    /**
     * 厂商 `POST /v1/software/release`：放行 / 禁止放行软件（写/删白名单 `/<key>/**`）。
     * <p>`type=1` 放行；`type=0` 禁止（**只看第一个存在的软件**，删到即 true、删不到即 false）；
     * `list` 为空直接 true。规则见 `ISoftwareCommandService.releaseSoftware`。
     */
    @org.springframework.web.bind.annotation.PostMapping("/release")
    public ResponseDTO<Boolean> releaseSoftware(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ReleaseSoftwareDTO form) {
        return ResponseDTO.ok(softwareCommandService.releaseSoftware(form.getType(), form.getList()));
    }

    /**
     * 厂商 `POST /v1/software`：新增软件（**controller 上有 `@Transactional`**）。
     * <p>三道闸照抄 `SoftwareController.java:169-182`：key 是保留字 `api` → 400；key 已存在 → 400；name 已存在 → 400。
     * <p>其余校验（name/key 长度与格式、desc 长度）见 `ISoftwareCommandService`。
     */
    @org.springframework.web.bind.annotation.PostMapping
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareDTO> addSoftware(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.SoftwareFormDTO form) {
        var vo = com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO.builder()
                .key(form.getKey()).name(form.getName()).nameFr(form.getNameFr()).nameUs(form.getNameUs())
                .type(form.getType()).routerKey(form.getRouterKey())
                .desc(form.getDesc()).location(form.getLocation())
                .remoteIp(form.getRemoteIp()).remoteName(form.getRemoteName()).remotePassword(form.getRemotePassword())
                .framework(form.getFramework()).execName(form.getExecName()).icon(form.getIcon())
                .screenShotList(form.getScreenShotList()).roleListKey(form.getRoleListKey())
                .build();
        var saved = softwareWriteCase.addSoftware(vo);
        // 厂商返回的是**Software（23 键）**，不是列表项（27 键）
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.SoftwareListAssembler.toResponse(saved));
    }

    @GetMapping
    public ResponseDTO<SoftwareListDTO> getSoftwareList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "key") String order,
            @RequestParam(required = false, defaultValue = "asc") String sort,
            HttpServletRequest request) {
        if (name != null && name.length() > 36) {
            throw new WrongArgumentException("software.search.length.invalid");
        }
        return ResponseDTO.ok(SoftwareListAssembler.toResponse(softwareQueryService.querySoftwareList(
                PageUtils.normalizePage(page), PageUtils.normalizeSize(size),
                name, type, order, sort, request.getHeader(FORWARDED_HOST_HEADER))));
    }

    /**
     * 厂商 `PUT /v1/software/{id}/licence`：设置软件授权码（body `{activeCode}`；与 `POST /v1/licence`
     * 的请求体同形状，故复用 `ActiveLicenceDTO`）。
     *
     * <p>校验不通过（含"授权码里没有这个软件"）⇒ 400 `licence.activation.code.error`；
     * 通过 ⇒ 只写 `g_software.active_code`，响应是**软件详情**（不是 LicenceInfo）。
     * 规则见 `ISoftwareCommandService.activateLicence`。
     */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/licence")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareInfoDTO> activeSoftware(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            com.csm.paas.core.api.dto.ActiveLicenceDTO form) {
        if (softwareCommandService.activateLicence(id, form == null ? null : form.getActiveCode()).isEmpty()) {
            throw new WrongArgumentException("licence.activation.code.error");
        }
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.SoftwareInfoAssembler.toResponse(
                softwareDetailService.queryDetail(id)));
    }

    /**
     * 厂商 `DELETE /v1/software/{id}/licence`：清除授权码（只把 `active_code` 置 NULL），
     * 响应同样是软件详情。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/licence")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareInfoDTO> resetSoftwareLicence(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        softwareCommandService.resetLicence(id);
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.SoftwareInfoAssembler.toResponse(
                softwareDetailService.queryDetail(id)));
    }

    /**
     * 厂商 `DELETE /v1/software/{id}/relate_service`：删除软件关联的服务（body `{list:[服务名]}`）。
     *
     * <p>照抄点：入参虽然叫 `serviceIdList`，实际是拿去跟 `g_software_app.app_name` 比的**服务名**；
     * 软件不存在、或与现有服务无交集 ⇒ **直接返回**（不报错）。响应是软件详情。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/relate_service")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareInfoDTO> unRelateServices(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            com.csm.paas.core.api.dto.RelateServicesDTO form) {
        softwareCommandService.unRelateServices(id, form == null ? null : form.getList());
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.SoftwareInfoAssembler.toResponse(
                softwareDetailService.queryDetail(id)));
    }

    // ---------- 启停与状态（厂商 `SoftwareController:518-660`）----------

    /**
     * 厂商 `GET /v1/software/{id}/start`：`CS` 走 SSH（已在跑直接返回 RUNNING，否则 nohup 启动），
     * 其它框架把非 RUNNING 的服务批量启动。响应 `{status}`。
     */
    @org.springframework.web.bind.annotation.GetMapping("/{id}/start")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareStatusDTO> startSoftware(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        return ResponseDTO.ok(com.csm.paas.core.api.dto.SoftwareStatusDTO.builder()
                .status(softwareLifecycleService.start(namespace, id)).build());
    }

    /** 厂商 `GET /v1/software/{id}/stop`：`CS` 走 SSH（`pkill -f`），其它框架批量停止非 STOP 的服务。 */
    @org.springframework.web.bind.annotation.GetMapping("/{id}/stop")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareStatusDTO> stopSoftware(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        return ResponseDTO.ok(com.csm.paas.core.api.dto.SoftwareStatusDTO.builder()
                .status(softwareLifecycleService.stop(namespace, id)).build());
    }

    /**
     * 厂商 `GET /v1/software/{id}/status`：`CS` ⇒ `pgrep` 判定；其它 ⇒ 按该软件的服务列表折算
     * （无服务 STOP / 全 RUNNING ⇒ RUNNING / 部分 ⇒ `PART_RUN`）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/{id}/status")
    public ResponseDTO<com.csm.paas.core.api.dto.SoftwareStatusDTO> csStatus(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        return ResponseDTO.ok(com.csm.paas.core.api.dto.SoftwareStatusDTO.builder()
                .status(softwareLifecycleService.queryStatus(namespace, id)).build());
    }
}
