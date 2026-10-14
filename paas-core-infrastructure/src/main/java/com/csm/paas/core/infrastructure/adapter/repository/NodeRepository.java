package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.node.adapter.port.INodeRemotePort;
import com.csm.paas.core.domain.node.adapter.repository.INodeRepository;
import com.csm.paas.core.domain.node.model.valobj.NodeSummaryVO;
import com.csm.paas.core.infrastructure.gateway.ServiceRuntimeGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 节点仓储 + 节点 SSH 端口的实现。
 *
 * <p>为什么一个类实现两个域端口：本仓库的改动白名单只放行
 * `infrastructure/adapter/repository/NodeRepository.java` 这一个基础设施文件，
 * 不允许新增端口实现文件；而"读集群节点/Prometheus"（仓储）与"对任意节点 ip 执行 SSH"（端口）
 * 都是 `/v1/pub` 节点管理簇的必需外部依赖，故合并在此，读取语义与执行语义分方法区隔。
 *
 * <p>照抄点：
 * <ul>
 *   <li>节点投影照厂商 `utils/K8sObjectUtil.getNodeIpInfo:116-146` 与
 *       `NodeVersionServiceImpl.detectNodeVersion:55-83`；</li>
 *   <li>Prometheus URL **不做 URL 编码**（厂商 `ComputerService.getNodeDisk:2124-2127`
 *       是把带空格与引号的 PromQL 直接拼进 `?query=`），超时 10000ms（`:2131`）；</li>
 *   <li>SSH 连接参数取 `gaop.ssh.*`（厂商 `config/SshConfig` + `application.yml:71-75`），
 *       每条命令一个 exec channel，用完断连（厂商 `SshUtil.getRunCommand:379`）。</li>
 * </ul>
 *
 * <p><b>类 C</b>：本环境无节点可 SSH、Prometheus 也不可达 ⇒ 代码齐全，不做在线验证。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class NodeRepository implements INodeRepository, INodeRemotePort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ServiceRuntimeGateway serviceRuntimeGateway;

    @Value("${prometheus.system-monitoring:http://localhost:30003}")
    private String systemMonitoring;

    @Value("${gaop.ssh.username:root}")
    private String sshUsername;

    @Value("${gaop.ssh.password:123456}")
    private String sshPassword;

    @Value("${gaop.ssh.port:22}")
    private int sshPort;

    // ---------------------------------------------------------------- INodeRepository

    @Override
    public List<NodeSummaryVO> listNodes() {
        List<io.fabric8.kubernetes.api.model.Node> items = serviceRuntimeGateway.listNodes();
        List<NodeSummaryVO> out = new ArrayList<>(items.size());
        for (io.fabric8.kubernetes.api.model.Node item : items) {
            Map<String, String> labels = item.getMetadata() == null ? null : item.getMetadata().getLabels();
            String internalIp = null;
            String hostname = null;
            List<String> addresses = new ArrayList<>();
            io.fabric8.kubernetes.api.model.NodeStatus status = item.getStatus();
            if (status != null && status.getAddresses() != null) {
                for (io.fabric8.kubernetes.api.model.NodeAddress address : status.getAddresses()) {
                    addresses.add(address.getAddress());
                    if ("InternalIP".equals(address.getType())) {
                        internalIp = address.getAddress();
                    }
                    if ("Hostname".equals(address.getType())) {
                        hostname = address.getAddress();
                    }
                }
            }
            Integer cpuAllocatable = null;
            if (status != null && status.getAllocatable() != null
                    && status.getAllocatable().get("cpu") != null) {
                cpuAllocatable = status.getAllocatable().get("cpu").getNumericalAmount().intValue();
            }
            String kubeletVersion = status == null || status.getNodeInfo() == null
                    ? null : status.getNodeInfo().getKubeletVersion();
            out.add(NodeSummaryVO.builder()
                    .name(item.getMetadata() == null ? null : item.getMetadata().getName())
                    .internalIp(internalIp)
                    .hostname(hostname)
                    .kubeletVersion(kubeletVersion)
                    .labels(labels)
                    .cpuAllocatable(cpuAllocatable)
                    .addresses(addresses)
                    .build());
        }
        return out;
    }

    @Override
    public Optional<Double> queryPrometheusScalar(String query) {
        // 厂商把 query 原样拼进 URL（不编码）；这里保持一致。
        String url = systemMonitoring + "/api/v1/query?query=" + query;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(10000))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode data = MAPPER.readTree(response.body()).get("data");
            if (data == null) {
                return Optional.empty();
            }
            JsonNode result = data.get("result");
            if (result == null || !result.isArray() || result.isEmpty()) {
                return Optional.empty();
            }
            JsonNode value = result.get(0).get("value");
            if (value == null || value.size() < 2) {
                return Optional.empty();
            }
            String sizeStr = value.get(1).asText();
            if (isBlank(sizeStr)) {
                return Optional.empty();
            }
            return Optional.of(Double.parseDouble(sizeStr));
        } catch (Exception e) {
            // 厂商 `:2146-2149` 捕获后把 size 落 0L；是否落 0 由域层决定，这里只报"没取到"。
            log.error("请求普罗米修斯失败: {}", e.getMessage(), e);
            throw new IllegalStateException("请求普罗米修斯失败: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- INodeRemotePort

    @Override
    public boolean reachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            log.error("连接地址为:" + host + "获取连接状态失败,异常为：", e);
            return false;
        }
    }

    @Override
    public String exec(String host, String command) {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(sshUsername, host, sshPort);
            session.setPassword(sshPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("userauth.gssapi-with-mic", "no");
            session.connect(30000);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channel.setOutputStream(out);
            channel.connect();
            while (!channel.isClosed()) {
                Thread.sleep(50);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("ssh连接服务器{}失败", host, e);
            throw new IllegalStateException("SSH 执行失败: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
