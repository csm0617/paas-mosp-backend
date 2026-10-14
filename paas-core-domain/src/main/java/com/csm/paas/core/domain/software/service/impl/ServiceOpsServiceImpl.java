package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.remote.adapter.port.ISshPort;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.NodeInfoVO;
import com.csm.paas.core.domain.software.service.IServiceOpsService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 服务域运维动作（规则见 {@link IServiceOpsService}）。**类 C：本环境不可验证**（无节点可 SSH）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceOpsServiceImpl implements IServiceOpsService {

    /** 厂商在多处**写死** `kube-system`（`:2336`、`:3356` 的命令里）。 */
    private static final String KUBE_SYSTEM_NAMESPACE = "kube-system";

    private final IServiceRuntimePort serviceRuntimePort;
    private final ISshPort sshPort;

    /** 厂商 `node.label`（`application.yml:195-196`，默认 `control-plane`）：master 节点标签的后缀。 */
    @Value("${node.label:control-plane}")
    private String nodeRole;

    @Override
    public boolean schedulingNode(String name, Integer type) {
        List<NodeInfoVO> nodes;
        try {
            nodes = serviceRuntimePort.listNodes();
        } catch (RuntimeException e) {
            log.error("获取node节点信息失败，异常为：", e);
            return false;
        }
        // 厂商把所有节点名收进 ipList（变量名叫 ipList，装的是节点名——照抄）
        List<String> nodeNames = new ArrayList<>();
        String masterIp = null;
        for (NodeInfoVO node : nodes) {
            nodeNames.add(node.getName());
            // 厂商 `labels.containsKey(nodeRole)`；labels 为 null 时会 NPE，我们按"不含该标签"处理
            if (node.getLabels() != null && node.getLabels().containsKey("node-role.kubernetes.io/" + nodeRole)) {
                masterIp = node.getFirstAddress();
            }
        }
        log.info("获取到master节点ip为：" + masterIp);
        if (!nodeNames.contains(name)) {
            throw new WrongArgumentException("service.node.name.not.found", name);
        }
        // 厂商：`connectSession(client, 30)` 抛异常 ⇒ 关闭 client 后 `return Response.ok(false)`（不报错）。
        // 端口层没有独立的"先连接"方法，这里用一条无害命令探测可达性；masterIp 取不到时同样按"连不上"处理。
        try {
            if (masterIp == null) {
                throw new IllegalStateException("没有找到 master 节点 IP");
            }
            sshPort.execOnHost(masterIp, "true");
        } catch (Exception e) {
            log.error("连接服务器出错", e);
            return false;
        }
        // 厂商两个 `if` 互相独立：其它 type 值既不执行命令、也照样返回 true
        if (type != null && type == 0) {
            try {
                sshPort.execOnHost(masterIp, "kubectl cordon " + name);
            } catch (Exception e) {
                throw new WrongArgumentException("service.stop.schedule.failed", name);
            }
        }
        if (type != null && type == 1) {
            try {
                sshPort.execOnHost(masterIp, "kubectl uncordon " + name);
            } catch (Exception e) {
                throw new WrongArgumentException("service.start.schedule.failed", name);
            }
        }
        return true;
    }

    @Override
    public boolean restartBaseService(String podName) {
        List<NodeInfoVO> nodes;
        try {
            nodes = serviceRuntimePort.listNodes();
        } catch (RuntimeException e) {
            log.error("获取node节点信息失败，异常为：", e);
            return false;
        }
        String masterIp = null;
        for (NodeInfoVO node : nodes) {
            if (node.getLabels() != null && node.getLabels().containsKey("node-role.kubernetes.io/" + nodeRole)) {
                masterIp = node.getFirstAddress();
            }
        }
        log.info("获取到master节点ip为：" + masterIp);
        // 厂商：连接失败 ⇒ 返回 false（不报错）
        try {
            if (masterIp == null) {
                throw new IllegalStateException("没有找到 master 节点 IP");
            }
            sshPort.execOnHost(masterIp, "true");
        } catch (Exception e) {
            log.error("连接服务器出错", e);
            return false;
        }
        try {
            sshPort.execOnHost(masterIp,
                    "kubectl delete pod " + podName + " -n " + KUBE_SYSTEM_NAMESPACE + " --force --grace-period=0");
        } catch (Exception e) {
            throw new WrongArgumentException("ssh.delete.base.service.pod.failed", podName);
        }
        return true;
    }

    @Override
    public boolean deletePods(String namespace, List<String> podNames, Boolean force) {
        if (podNames == null) {
            return true;
        }
        for (String podName : podNames) {
            try {
                serviceRuntimePort.deletePod(namespace, podName, force);
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                // 厂商：`throw new SystemException("service.delete.pod.failed", podName, e.getResponseBody())`
                throw new com.csm.paas.core.types.exception.SystemException(
                        "service.delete.pod.failed", podName, e.getMessage());
            }
        }
        return true;
    }
}
