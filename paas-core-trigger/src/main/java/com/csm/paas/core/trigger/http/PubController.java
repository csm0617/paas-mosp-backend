package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.PubInterfaceInfoDTO;
import com.csm.paas.core.api.dto.PubNodeAddByInitDTO;
import com.csm.paas.core.api.dto.PubNodeAlertSettingDTO;
import com.csm.paas.core.api.dto.PubNodeConnectInfoDTO;
import com.csm.paas.core.api.dto.PubNodeDiskInfoDTO;
import com.csm.paas.core.api.dto.PubNodeNetNameIpDTO;
import com.csm.paas.core.api.dto.PubVipInfoDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.node.model.valobj.InterfaceInfoVO;
import com.csm.paas.core.domain.node.model.valobj.NodeAddFormVO;
import com.csm.paas.core.domain.node.model.valobj.NodeAlertSettingVO;
import com.csm.paas.core.domain.node.model.valobj.NodeConnectInfoVO;
import com.csm.paas.core.domain.node.model.valobj.NodeDiskVO;
import com.csm.paas.core.domain.node.model.valobj.NodeNetNameIpVO;
import com.csm.paas.core.domain.node.model.valobj.VipItemVO;
import com.csm.paas.core.domain.node.service.IComputerResourceService;
import com.csm.paas.core.domain.node.service.INodeManagerService;
import com.csm.paas.core.domain.node.service.IPubServiceQueryService;
import com.csm.paas.core.domain.node.service.IServerMaintenanceService;
import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserSessionRepository;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 公共 API（厂商 `/v1/pub` 簇）——节点管理 / 计算机资源 / 设置 / VIP。
 *
 * <p>来源（厂商反编译源码）：
 * <ul>
 *   <li>`controller/pub/PublicNodeManagerController.java`（7 条）；</li>
 *   <li>`controller/pub/PublicComputerController.java` 的 `/nodeDisk`、`/getNodeAlertSetting`、`/setNodeAlertSetting`；</li>
 *   <li>`controller/pub/PublicController.java` 的 `/check/password`、`/updateQuickStart`、`/clear_login_status`、
 *       `/k8s_more_node`、`/ping`；</li>
 *   <li>`controller/pub/PubVipController.java` 的 `/vip`、`/vip/interfaces`。</li>
 * </ul>
 *
 * <p>本类**不**改已存在的 {@code PublicController}（它只覆盖 `/v1/pub/nodePortUsed`）。
 *
 * <p>照抄约定：
 * <ul>
 *   <li>响应信封一律 {@link ResponseDTO#ok(Object)}，形状与厂商 {@code Response} 一致；</li>
 *   <li>参数错误一律 HTTP 400 + i18n 键（厂商 {@code WrongArgumentException} + {@code ResponseExceptionConfig.wrongArg}）；</li>
 *   <li>{@code /check/password} 不匹配时厂商是 {@code Response.error(500, "校验失败，输入密码不正确")}
 *       ——**HTTP 仍 200、body 里 code=500**，所以这里是 {@code ResponseDTO.fail(500, ...)}。</li>
 * </ul>
 *
 * <p><b>类 C</b>：节点管理整簇与 {@code /nodeDisk} 依赖 SSH/ansible/Prometheus，
 * 本环境无可连节点 ⇒ 代码齐全、不做在线验证。
 */
@Slf4j
@RestController
@RequestMapping("/v1/pub")
@RequiredArgsConstructor
public class PubController {

    private final INodeManagerService nodeManagerService;
    private final IServerMaintenanceService serverMaintenanceService;
    private final IPubServiceQueryService pubServiceQueryService;
    private final IComputerResourceService computerResourceService;
    private final ISettingRepository settingRepository;
    private final IUserSessionRepository userSessionRepository;
    private final PubVipClient pubVipClient;

    /** 厂商 `SettingService.getCheckPassword:147-157` 的兜底密码（**原样常量**）。 */
    private static final String DEFAULT_CHECK_PASSWORD = "DB2BD91882F82F2179873D03A2FE9B6F";

    // ================================================================ 节点管理

    /** 厂商 `PublicNodeManagerController.testConnectServer:41-47`。 */
    @GetMapping("/testConnect")
    public ResponseDTO<PubNodeConnectInfoDTO> testConnectServer(@RequestParam String ip) {
        if (!isIp(ip)) {
            // 厂商 `IpUtils.isIp:7-10` 的正则，原样。
            throw new WrongArgumentException("k8s.add.node.ip.error");
        }
        return ResponseDTO.ok(toDto(nodeManagerService.testConnectServer(ip)));
    }

    /** 厂商 `PublicNodeManagerController.existNodeName:52-60`（厂商把耗时也打进日志）。 */
    @GetMapping("/existNodeName")
    public ResponseDTO<Boolean> existNodeName(@RequestParam String name) {
        if (isBlank(name)) {
            throw new WrongArgumentException("k8s.add.node.new.node-name.null");
        }
        long startTime = System.currentTimeMillis();
        Boolean result = nodeManagerService.existNodeName(name);
        log.info("执行花费时为: {}毫秒", System.currentTimeMillis() - startTime);
        return ResponseDTO.ok(result);
    }

    /** 厂商 `PublicNodeManagerController.uninstallNode:65-70`。 */
    @PostMapping("/uninstallNode")
    public ResponseDTO<Boolean> uninstallNode(@RequestParam String ip, @RequestParam String nodeName) {
        long startTime = System.currentTimeMillis();
        Boolean result = nodeManagerService.uninstallNode(ip, nodeName);
        log.info("执行花费时为: {}毫秒", System.currentTimeMillis() - startTime);
        return ResponseDTO.ok(result);
    }

    /** 厂商 `PublicNodeManagerController.addNodeInit:75-80`（第一步）。 */
    @PostMapping("/initHost")
    public ResponseDTO<Boolean> addNodeInit(@RequestBody PubNodeAddByInitDTO form) {
        long startTime = System.currentTimeMillis();
        Boolean result = nodeManagerService.addNodeInitHost(toVo(form));
        log.info("执行花费时为: {}毫秒", System.currentTimeMillis() - startTime);
        return ResponseDTO.ok(result);
    }

    /** 厂商 `PublicNodeManagerController.addNodeInstallDockerEnv:85-90`（第二步）。 */
    @PostMapping("/setDockerConfig")
    public ResponseDTO<Boolean> addNodeInstallDockerEnv(@RequestBody PubNodeAddByInitDTO form) {
        long startTime = System.currentTimeMillis();
        Boolean result = nodeManagerService.addNodeInstallDockerEnv(toVo(form));
        log.info("执行花费时为: {}毫秒", System.currentTimeMillis() - startTime);
        return ResponseDTO.ok(result);
    }

    /** 厂商 `PublicNodeManagerController.finallyAddNode:95-100`（第三步）。 */
    @PostMapping("/finallyAddNode")
    public ResponseDTO<Boolean> finallyAddNode(@RequestBody PubNodeAddByInitDTO form) {
        long startTime = System.currentTimeMillis();
        Boolean result = nodeManagerService.finallyAddNode(toVo(form));
        log.info("执行花费时为: {}毫秒", System.currentTimeMillis() - startTime);
        return ResponseDTO.ok(result);
    }

    /** 厂商 `PublicNodeManagerController.addFdfsByNode:105-110` ⇒ `SoftwareService.configFileService`。 */
    @PostMapping("/addFdfsByNode")
    public ResponseDTO<Boolean> addFdfsByNode(@RequestParam String ip) {
        long startTime = System.currentTimeMillis();
        Boolean result = nodeManagerService.configFileService(ip);
        log.info("执行花费时为: {}毫秒", System.currentTimeMillis() - startTime);
        return ResponseDTO.ok(result);
    }

    // ================================================================ 集群 / 服务

    /**
     * 厂商 `PublicController.serviceKeyInfo:2029-2068`（成功分支）。
     * <p>厂商那条 500 分支的 i18n 键是 `service.get.service.name.list.error`。
     */
    @GetMapping("/serviceKeyList")
    public ResponseDTO<List<String>> serviceKeyInfo(@RequestParam String name,
                                                    @RequestParam(required = false) String serviceName) {
        return ResponseDTO.ok(pubServiceQueryService.serviceKeyList(name, serviceName));
    }

    /** 厂商 `PublicController.unifyClusterEtcd:3700-3703`。 */
    @GetMapping("/unify/etcd")
    public ResponseDTO<Boolean> unifyClusterEtcd(@RequestParam String etcdNodeIp) {
        return ResponseDTO.ok(nodeManagerService.unifyClusterEtcd(etcdNodeIp));
    }

    // ================================================================ 计算机资源

    /** 厂商 `PublicComputerController.getNodeDisk:139-142`。 */
    @GetMapping("/nodeDisk")
    public ResponseDTO<List<PubNodeDiskInfoDTO>> getNodeDisk() {
        List<PubNodeDiskInfoDTO> out = new ArrayList<>();
        for (NodeDiskVO vo : computerResourceService.getNodeDisk()) {
            out.add(PubNodeDiskInfoDTO.builder().name(vo.getName()).size(vo.getSize()).build());
        }
        return ResponseDTO.ok(out);
    }

    /** 厂商 `PublicComputerController.setNodeAlertSetting:116-119`（响应 data 为 null）。 */
    @PostMapping("/setNodeAlertSetting")
    public ResponseDTO<Void> setNodeAlertSetting(@RequestBody PubNodeAlertSettingDTO nodeAlertSetting) {
        computerResourceService.setNodeAlertSetting(NodeAlertSettingVO.builder()
                .cpuUsageAlert(nodeAlertSetting.getCpuUsageAlert())
                .memoryUsageAlert(nodeAlertSetting.getMemoryUsageAlert())
                .diskUsageAlert(nodeAlertSetting.getDiskUsageAlert())
                .build());
        return ResponseDTO.ok(null);
    }

    /** 厂商 `PublicComputerController.getNodeAlertSetting:124-126`。 */
    @GetMapping("/getNodeAlertSetting")
    public ResponseDTO<PubNodeAlertSettingDTO> getNodeAlertSetting() {
        NodeAlertSettingVO vo = computerResourceService.getNodeAlertSetting();
        return ResponseDTO.ok(PubNodeAlertSettingDTO.builder()
                .cpuUsageAlert(vo.getCpuUsageAlert())
                .memoryUsageAlert(vo.getMemoryUsageAlert())
                .diskUsageAlert(vo.getDiskUsageAlert())
                .build());
    }

    // ================================================================ 服务器运维（原 PublicFdfsController）

    /** 厂商 `PublicFdfsController.restartIpFastdfs:207-210`（body 是 ip 字符串数组）。 */
    @PostMapping("/restartFdfs")
    public ResponseDTO<Boolean> restartIpFastdfs(@RequestBody List<String> ipList) {
        return ResponseDTO.ok(serverMaintenanceService.restartIpFastdfs(ipList));
    }

    /** 厂商 `PublicFdfsController.connectServerTest:215-218`。 */
    @PostMapping("/connect")
    public ResponseDTO<Boolean> connectServerTest() {
        return ResponseDTO.ok(serverMaintenanceService.connectServerTest());
    }

    /** 厂商 `PublicFdfsController.restartServer:223-226`。 */
    @PostMapping("/restartServer")
    public ResponseDTO<Boolean> restartServer(@RequestParam String master, @RequestParam String worker) {
        return ResponseDTO.ok(serverMaintenanceService.restartServer(master, worker));
    }

    /** 厂商 `PublicFdfsController.shutdownServer:231-234`。 */
    @PostMapping("/shutdownServer")
    public ResponseDTO<Boolean> shutdownServer(@RequestParam String master, @RequestParam String worker) {
        return ResponseDTO.ok(serverMaintenanceService.shutdownServer(master, worker));
    }

    // ================================================================ 设置 / 杂项

    /**
     * 厂商 `PublicController.checkSecondaryPassword:2134-2153`。
     * <p>怪点照抄：`type` 既不是 0 也不是 1 时 {@code settingKey} 保持空串，
     * 于是拿空键去查（厂商查不到就回默认密码）。
     */
    @PostMapping("/check/password")
    public ResponseDTO<Boolean> checkSecondaryPassword(@RequestParam Integer type, @RequestParam String password) {
        if (isBlank(password)) {
            throw new WrongArgumentException("pub.http.empty.password.validation");
        }
        String settingKey = "";
        if (type == 0) {
            settingKey = "system.check.password";
        }
        if (type == 1) {
            settingKey = "system.backup.password";
        }
        String checkPassword = settingRepository.queryValue(settingKey).orElse(DEFAULT_CHECK_PASSWORD);
        if (password.equals(checkPassword)) {
            return ResponseDTO.ok(true);
        }
        return ResponseDTO.fail(500, "校验失败，输入密码不正确");
    }

    /** 厂商 `PublicController.settingQuickStart:2407-2429`。 */
    @PostMapping("/updateQuickStart")
    public ResponseDTO<Boolean> settingQuickStart(@RequestParam Integer status) {
        // 厂商先 `userService.getSettings(key)` 判空；空 ⇒ ok(false) 且**不写**。
        if (settingRepository.queryValue("system.init.quick_start").isEmpty()) {
            return ResponseDTO.ok(false);
        }
        if (status == 1) {
            settingRepository.upsertValue("system.init.quick_start", "true", "");
        }
        if (status == 0) {
            settingRepository.upsertValue("system.init.quick_start", "false", "");
        }
        return ResponseDTO.ok(true);
    }

    /** 厂商 `PublicController.clearLoginStatus:586-590`（`redisService.deleteAllRedisKey()`）。 */
    @PostMapping("/clear_login_status")
    public ResponseDTO<Boolean> clearLoginStatus() {
        userSessionRepository.deleteAllSessions();
        return ResponseDTO.ok(true);
    }

    /** 厂商 `PublicController.isK8sMoreNode:3685-3687`。 */
    @GetMapping("/k8s_more_node")
    public ResponseDTO<Boolean> isK8sMoreNode() {
        return ResponseDTO.ok(nodeManagerService.k8sMoreNode());
    }

    /**
     * 厂商 `PublicController.pingTest:3588-3610`：
     * `GET http://<url>/api/gaop/v1/pub/connectedStatus`，取 body 的 `data` 布尔；
     * 任何异常 ⇒ {@code ok(false)}（注意：是**成功信封里的 false**，不是错误）。
     */
    @GetMapping("/ping")
    public ResponseDTO<Boolean> pingTest(@RequestParam String url) {
        return ResponseDTO.ok(pubVipClient.ping(url));
    }

    // ================================================================ VIP

    /** 厂商 `PubVipController.interfaces:64-80`（按 `vip.ignore-prefix-list` 过滤）。 */
    @GetMapping("/vip/interfaces")
    public ResponseDTO<List<PubInterfaceInfoDTO>> interfaces() {
        List<PubInterfaceInfoDTO> out = new ArrayList<>();
        for (InterfaceInfoVO vo : pubVipClient.getInterfaces()) {
            String name = vo.getName();
            List<String> ignorePrefixList = pubVipClient.getIgnorePrefixList();
            boolean ignore = false;
            if (ignorePrefixList != null) {
                for (String prefix : ignorePrefixList) {
                    if (name != null && name.startsWith(prefix)) {
                        ignore = true;
                        break;
                    }
                }
            }
            if (ignore) {
                continue;
            }
            out.add(PubInterfaceInfoDTO.builder()
                    .index(vo.getIndex()).name(vo.getName()).hardwareAddr(vo.getHardwareAddr())
                    .flags(vo.getFlags()).addresses(vo.getAddresses()).build());
        }
        return ResponseDTO.ok(out);
    }

    /** 厂商 `PubVipController.getVipList:84-88`。 */
    @GetMapping("/vip")
    public ResponseDTO<List<PubVipInfoDTO>> getVipList() {
        return ResponseDTO.ok(getVipInfos());
    }

    /** 厂商 `PubVipController.addVip:109-115`（写入时**固定补 `/32`**）。 */
    @PostMapping("/vip")
    public ResponseDTO<List<PubVipInfoDTO>> addVip(@RequestBody PubVipInfoDTO vip) {
        pubVipClient.addVip(vip.getNetName(), vip.getIp() + "/32");
        return ResponseDTO.ok(getVipInfos());
    }

    /** 厂商 `PubVipController.deleteVip:119-129`（受保护 ip 先抛 400）。 */
    @DeleteMapping("/vip")
    public ResponseDTO<List<PubVipInfoDTO>> deleteVip(@RequestBody PubVipInfoDTO vip) {
        List<String> protectedIpList = pubVipClient.getProtectedIpList();
        if (protectedIpList != null && protectedIpList.contains(vip.getIp())) {
            throw new WrongArgumentException("vip.delete.protected.ip", vip.getIp());
        }
        pubVipClient.deleteVip(vip.getNetName(), vip.getIp() + "/32");
        return ResponseDTO.ok(getVipInfos());
    }

    /** 厂商 `PubVipController.getVipInfos:90-105`：读回来时把 `ip/掩码` 截成纯 ip。 */
    private List<PubVipInfoDTO> getVipInfos() {
        List<VipItemVO> vips = pubVipClient.getVips();
        List<PubVipInfoDTO> list = new ArrayList<>();
        for (VipItemVO vip : vips) {
            String ip = vip.getVip();
            String[] split = ip.split("/");
            if (split.length >= 1) {
                ip = split[0];
            }
            list.add(PubVipInfoDTO.builder().ip(ip).netName(vip.getInterfaceName()).build());
        }
        return list;
    }

    // ================================================================ 工具

    /** 厂商 `IpUtils.isIp:7-10` 的正则，逐字照抄。 */
    private static boolean isIp(String ip) {
        if (ip == null) {
            return false;
        }
        return ip.matches("([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static NodeAddFormVO toVo(PubNodeAddByInitDTO dto) {
        return NodeAddFormVO.builder()
                .ip(dto.getIp())
                .hostname(dto.getHostname())
                .labelNvidia(dto.getLabelNvidia())
                .netWorkInterface(dto.getNetWorkInterface())
                .build();
    }

    private static PubNodeConnectInfoDTO toDto(NodeConnectInfoVO vo) {
        List<PubNodeNetNameIpDTO> netWorkInfo = new ArrayList<>();
        if (vo.getNetWorkInfo() != null) {
            for (NodeNetNameIpVO net : vo.getNetWorkInfo()) {
                netWorkInfo.add(PubNodeNetNameIpDTO.builder()
                        .netName(net.getNetName()).ipList(net.getIpList()).build());
            }
        }
        return PubNodeConnectInfoDTO.builder()
                .connect(vo.getConnect())
                .hostname(vo.getHostname())
                .netWorkInfo(netWorkInfo)
                .build();
    }
}

/**
 * VIP 组件（vip daemon）的 HTTP 客户端 + `/v1/pub/ping` 的探活。
 *
 * <p><b>为什么放在 trigger 层</b>：厂商的 VIP 是 Feign 客户端
 * （{@code service/vip/VipDaemonApi.java}，`GET /api/vips`、`POST /api/vips/add`、
 * `POST /api/vips/delete`、`GET /api/interfaces`），需要一个"外部 HTTP 端口"实现；
 * 而本次改动的文件白名单只放行 domain/node/** 与 trigger 的 PubController.java，
 * 不允许新增 infrastructure 端口实现文件，所以把这段纯 I/O 适配放在控制器同文件里
 * （包级私有类，不进对外契约），VIP 的**业务判断**（忽略前缀过滤、受保护 ip 校验、
 * ip 去掩码）仍在 {@link PubController} 与调用点。
 *
 * <p>照抄点：
 * <ul>
 *   <li>目标地址 `http://{vip.host}:{vip.port}`（厂商 `application.yml:213-216`
 *       默认端口 5556）；</li>
 *   <li>`ignore-prefix-list` 默认 `lo/docker/kube-/flannel`、`protected-ip-list` 默认
 *       `192.168.0.100`（厂商 `application.yml:216-225`）；paas-core 目前没有这些配置项，
 *       故用 {@code @Value} 内联厂商默认值。</li>
 * </ul>
 */
@Slf4j
@Component
class PubVipClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${vip.host:localhost}")
    private String host;

    @Value("${vip.port:5556}")
    private String port;

    /** 厂商 yml 里是 YAML 列表；这里用逗号串承载同一批默认值。 */
    @Value("${vip.ignore-prefix-list:lo,docker,kube-,flannel}")
    private String ignorePrefixListRaw;

    @Value("${vip.protected-ip-list:192.168.0.100}")
    private String protectedIpListRaw;

    List<String> getIgnorePrefixList() {
        return splitList(ignorePrefixListRaw);
    }

    List<String> getProtectedIpList() {
        return splitList(protectedIpListRaw);
    }

    List<InterfaceInfoVO> getInterfaces() {
        JsonNode node = request("GET", "/api/interfaces", null);
        List<InterfaceInfoVO> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            List<String> addresses = new ArrayList<>();
            JsonNode addrNode = item.get("addresses");
            if (addrNode != null && addrNode.isArray()) {
                for (JsonNode a : addrNode) {
                    addresses.add(a.asText());
                }
            }
            out.add(InterfaceInfoVO.builder()
                    .index(item.hasNonNull("index") ? item.get("index").asInt() : null)
                    .name(item.hasNonNull("name") ? item.get("name").asText() : null)
                    .hardwareAddr(item.hasNonNull("hardwareAddr") ? item.get("hardwareAddr").asText() : null)
                    .flags(item.hasNonNull("flags") ? item.get("flags").asText() : null)
                    .addresses(addresses)
                    .build());
        }
        return out;
    }

    List<VipItemVO> getVips() {
        JsonNode node = request("GET", "/api/vips", null);
        List<VipItemVO> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            out.add(VipItemVO.builder()
                    .interfaceName(item.hasNonNull("interfaceName") ? item.get("interfaceName").asText() : null)
                    .vip(item.hasNonNull("vip") ? item.get("vip").asText() : null)
                    .build());
        }
        return out;
    }

    void addVip(String interfaceName, String vip) {
        request("POST", "/api/vips/add", VipItemVO.builder().interfaceName(interfaceName).vip(vip).build());
    }

    void deleteVip(String interfaceName, String vip) {
        request("POST", "/api/vips/delete", VipItemVO.builder().interfaceName(interfaceName).vip(vip).build());
    }

    /**
     * 厂商 `PublicController.pingTest:3588-3610`：
     * `GET http://<url>/api/gaop/v1/pub/connectedStatus` ⇒ body 的 `data`；异常 ⇒ false。
     */
    boolean ping(String url) {
        String finalUrl = "http://" + url + "/api/gaop/" + "v1/pub/connectedStatus";
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(finalUrl))
                    .timeout(Duration.ofMillis(10000)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("获取信息:" + response.body());
            JsonNode jsonObject = MAPPER.readTree(response.body());
            JsonNode data = jsonObject.get("data");
            return data != null && data.asBoolean();
        } catch (Exception e) {
            log.error("请求不到远程地址" + url + "连通测试接口，异常:", e);
            return false;
        }
    }

    private JsonNode request(String method, String path, Object body) {
        String url = "http://" + host + ":" + port + path;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(10000));
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                String payload = body == null ? "{}" : MAPPER.writeValueAsString(body);
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.body() == null || response.body().trim().isEmpty()) {
                return null;
            }
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            // 厂商 Feign 调用失败会冒成 500；这里保持"调用不通就是错"的语义。
            throw new IllegalStateException("vip daemon 调用失败: " + url + " " + e.getMessage(), e);
        }
    }

    private static List<String> splitList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String s : raw.split(",")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }
}
