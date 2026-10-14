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
}
