package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.node.adapter.port.INodeRemotePort;
import com.csm.paas.core.domain.node.adapter.repository.INodeRepository;
import com.csm.paas.core.domain.node.model.valobj.NodeAddFormVO;
import com.csm.paas.core.domain.node.model.valobj.NodeConnectInfoVO;
import com.csm.paas.core.domain.node.model.valobj.NodeIpInfoVO;
import com.csm.paas.core.domain.node.model.valobj.NodeNetNameIpVO;
import com.csm.paas.core.domain.node.model.valobj.NodeSummaryVO;
import com.csm.paas.core.domain.node.service.INodeManagerService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点管理实现（厂商 `service/SoftwareService.java` 的节点相关方法 + `impl/NodeVersionServiceImpl`）。
 *
 * <p><b>类 C</b>：本环境无节点可 SSH，代码齐全但不做在线验证。
 *
 * <p>照抄的怪点 / 注意点：
 * <ul>
 *   <li>{@code testConnectServer} 的 TCP 探活**只连 22 端口、超时 3000ms**，成功后立刻把
 *       {@code connect} 置 true（`:4700-4703`），所以"TCP 通但 SSH 不通"时接口是 400 而不是
 *       {@code {connect:true}}；</li>
 *   <li>{@code uninstallNode} 取 masterIp 用的是 {@code k8s.get.init.first.control.plane.ip.failed}
 *       这个键（`:4819`，与另外三个方法的 {@code k8s.get.master.ip.failed.not.uninstall} 不同），
 *       且**只在这个键上带参数**；</li>
 *   <li>{@code initHost} 的 playbook 是 `add-node-prepare.yml`、{@code setDockerConfig} 是
 *       `add-node-docker.yml`、{@code finallyAddNode} 是 `add-node-kubernetes.yml`，
 *       而三者写的是**同一个** `addNode` 配置文件（`:4928-4932`、`:5041-5045`、`:5122-5126`）；</li>
 *   <li>{@code configFileService}（addFdfsByNode）**不写**配置文件，直接跑 playbook（`:5203-5211`）。</li>
 * </ul>
 *
 * <p>命令内容逐字照抄 `utils/SshUtil.java`：
 * {@code getHostName:684-690}、{@code getNetName:504-509}、{@code getNetIpByNetName:644-651}、
 * {@code addOrDeleteNodeInitConfig:808-822}、{@code execAnsiblePlaybook:859-878}、
 * {@code updateHostName:792-806}、{@code fileExists:880-883}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeManagerServiceImpl implements INodeManagerService {

    /** 厂商 `SshUtil.PREPARE_ANSIBLE_SHELL:44`。 */
    private static final String PREPARE_ANSIBLE_SHELL = "prepare-ansible.sh";
    /** 厂商 `K8sObjectUtil.INITIAL_FIRST_CONTROL_PLANE_LABEL_KEY:43`。 */
    private static final String INITIAL_FIRST_CONTROL_PLANE_LABEL_KEY = "control-plane";
    /** 厂商 `K8sObjectUtil.INITIAL_FIRST_CONTROL_PLANE_LABEL_VALUE:44`。 */
    private static final String INITIAL_FIRST_CONTROL_PLANE_LABEL_VALUE = "first";
    /** 厂商 `SshUtil.getNetName:505` 的命令原文。 */
    private static final String NET_NAME_COMMAND =
            "ip -4 a | grep \"group default\" | awk '{print$2}' | awk -F ':' '{print$1}' "
                    + "| grep -v -E \"lo|weave|docker0|virbr0|br-*\" | awk 'BEGIN{ORS=\",\"}{print $0}'";
    /** 厂商 `AnsibleConfigConstant.DATA_PAAS_VIP_CONFIG_JSON:28`。 */
    private static final String VIP_CONFIG_JSON_PATH = "/data/paas/vip/config.json";

    private final INodeRepository nodeRepository;
    private final INodeRemotePort nodeRemotePort;

    @Value("${gaop.ssh.host:localhost}")
    private String sshHost;

    @Value("${gaop.ssh.password:123456}")
    private String sshPassword;

    @Value("${install.dir:/data/receive/k8s-v1.27.3/}")
    private String installDir;

    @Value("${mount.path:/data/paas/}")
    private String mountPath;

    // ---------------------------------------------------------------- testConnect

    @Override
    public NodeConnectInfoVO testConnectServer(String ip) {
        NodeConnectInfoVO connectInfo = new NodeConnectInfoVO();
        connectInfo.setConnect(false);
        // 厂商 `ClientGlobal.setG_connect_timeout(3000)` + `ClientGlobal.getSocket(address)`（22 端口）。
        if (!nodeRemotePort.reachable(ip, 22, 3000)) {
            log.error("连接地址为:{}获取连接状态失败", ip);
            return connectInfo;
        }
        connectInfo.setConnect(true);
        try {
            String hostName = run(ip, "hostname");
            if (hostName == null) {
                throw new WrongArgumentException("ssh.get.server.hostname.failed");
            }
            log.info("获取的服务器名称信息为: {}", hostName);
            connectInfo.setHostname(hostName);

            String netNames = run(ip, NET_NAME_COMMAND);
            if (netNames == null) {
                throw new WrongArgumentException("ssh.cannot.get.network.info");
            }
            log.info("获取的网卡信息: {}", netNames);
            List<NodeNetNameIpVO> list = new ArrayList<>();
            for (String netName : netNames.split(",")) {
                String netIpByNetName = run(ip, "ip -4 a | grep " + netName + " | grep global | awk '{print$2}' "
                        + "| awk -F '/' '{print$1}' | awk 'BEGIN{ORS=\",\"}{print $1}'");
                if (netIpByNetName == null) {
                    throw new WrongArgumentException("ssh.cannot.get.gateway.ip.network.info.empty", netName);
                }
                list.add(NodeNetNameIpVO.builder()
                        .netName(netName)
                        .ipList(Arrays.asList(netIpByNetName.split(",")))
                        .build());
            }
            connectInfo.setNetWorkInfo(list);
            return connectInfo;
        } catch (WrongArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("ssh连接服务器{}失败，无法通过远程连接获取服务器网卡信息", ip, e);
            throw new WrongArgumentException("ssh.connect.failed", ip);
        }
    }

    // ---------------------------------------------------------------- existNodeName

    @Override
    public boolean existNodeName(String name) {
        List<String> list = new ArrayList<>(5);
        try {
            for (NodeSummaryVO node : nodeRepository.listNodes()) {
                if (node == null) {
                    continue;
                }
                String nodeName = node.getName();
                list.add(nodeName);
            }
        } catch (RuntimeException e) {
            log.error("获取节点信息失败,无法验证是否存在节点名称:" + e.getMessage());
            return false;
        }
        return list.contains(name);
    }

    // ---------------------------------------------------------------- uninstallNode

    @Override
    public boolean uninstallNode(String ip, String nodeName) {
        NodeIpInfoVO nodeIpInfo = getNodeIpInfo();
        String masterIp = nodeIpInfo.getMasterIp();
        if (isBlank(masterIp)) {
            throw new WrongArgumentException("k8s.get.init.first.control.plane.ip.failed");
        }
        String hostPath = AnsibleConfigText.configPath(installDir);
        String configPath = AnsibleConfigText.addNodeConfigFilePath(installDir);
        String initHostConfig = AnsibleConfigText.masterUninstallNodeConfig(
                nodeIpInfo, ip, nodeName, readVipSection(), sshPassword, mountPath);
        log.info("生成的initHost配置:" + initHostConfig);

        if (!addOrDeleteNodeInitConfig(masterIp, hostPath, AnsibleConfigText.ADD_NODE_CONFIG, initHostConfig)) {
            throw new WrongArgumentException("k8s.add.node.set.ansible.config.failed");
        }
        if (!execAnsiblePlaybook(masterIp, configPath, "uninstall-node.yml", ip)) {
            throw new WrongArgumentException("k8s.uninstall.node.ansible.failed");
        }
        return true;
    }

    // ---------------------------------------------------------------- initHost

    @Override
    public boolean addNodeInitHost(NodeAddFormVO form) {
        NodeIpInfoVO nodeIpInfo = getNodeIpInfo();
        String masterIp = nodeIpInfo.getMasterIp();
        if (isBlank(masterIp)) {
            throw new WrongArgumentException("k8s.get.master.ip.failed.not.uninstall");
        }
        // 厂商先连"新节点"改 hostname，再连 master 写配置、跑 playbook（`:4905-4946`）。
        boolean updateHostName = updateHostName(form.getIp(), form.getHostname());
        log.info("更新hostname结果:{}", updateHostName);

        String hostPath = AnsibleConfigText.configPath(installDir);
        String configPath = AnsibleConfigText.addNodeConfigFilePath(installDir);
        String initHostConfig = AnsibleConfigText.addNodeConfig(
                nodeIpInfo, form, readVipSection(), sshPassword, mountPath);
        log.info("生成的initHost配置:" + initHostConfig);

        if (!addOrDeleteNodeInitConfig(masterIp, hostPath, AnsibleConfigText.ADD_NODE_CONFIG, initHostConfig)) {
            throw new WrongArgumentException("k8s.add.node.first.prepare.set.ansible.config.failed");
        }
        if (!execAnsiblePlaybook(masterIp, configPath, "add-node-prepare.yml", form.getIp())) {
            throw new WrongArgumentException("k8s.add.node.first.prepare.ansible.failed");
        }
        return true;
    }

    // ---------------------------------------------------------------- setDockerConfig

    @Override
    public boolean addNodeInstallDockerEnv(NodeAddFormVO form) {
        NodeIpInfoVO nodeIpInfo = getNodeIpInfo();
        String masterIp = nodeIpInfo.getMasterIp();
        if (isBlank(masterIp)) {
            throw new WrongArgumentException("k8s.get.master.ip.failed.not.uninstall");
        }
        String hostPath = AnsibleConfigText.configPath(installDir);
        String configPath = AnsibleConfigText.addNodeConfigFilePath(installDir);
        String initHostConfig = AnsibleConfigText.masterAddNodeConfig(
                nodeIpInfo, form, readVipSection(), sshPassword, mountPath);
        log.info("生成的initHost配置:" + initHostConfig);

        if (!addOrDeleteNodeInitConfig(masterIp, hostPath, AnsibleConfigText.ADD_NODE_CONFIG, initHostConfig)) {
            throw new WrongArgumentException("k8s.add.node.second.docker.set.ansible.config.failed");
        }
        if (!execAnsiblePlaybook(masterIp, configPath, "add-node-docker.yml", form.getIp())) {
            throw new WrongArgumentException("k8s.add.node.second.docker.ansible.failed");
        }
        return true;
    }

    // ---------------------------------------------------------------- finallyAddNode

    @Override
    public boolean finallyAddNode(NodeAddFormVO form) {
        NodeIpInfoVO nodeIpInfo = getNodeIpInfo();
        String masterIp = nodeIpInfo.getMasterIp();
        if (isBlank(masterIp)) {
            throw new WrongArgumentException("k8s.get.master.ip.failed.not.uninstall");
        }
        String hostPath = AnsibleConfigText.configPath(installDir);
        String configPath = AnsibleConfigText.addNodeConfigFilePath(installDir);
        String initHostConfig = AnsibleConfigText.masterAddNodeConfig(
                nodeIpInfo, form, readVipSection(), sshPassword, mountPath);
        log.info("生成的initHost配置:" + initHostConfig);

        if (!addOrDeleteNodeInitConfig(masterIp, hostPath, AnsibleConfigText.ADD_NODE_CONFIG, initHostConfig)) {
            throw new WrongArgumentException("k8s.add.node.third.cluster.set.ansible.config.failed");
        }
        if (!execAnsiblePlaybook(masterIp, configPath, "add-node-kubernetes.yml", form.getIp())) {
            throw new WrongArgumentException("k8s.add.node.third.cluster.ansible.failed");
        }
        return true;
    }

    // ---------------------------------------------------------------- addFdfsByNode

    @Override
    public boolean configFileService(String ip) {
        NodeIpInfoVO nodeIpInfo = getNodeIpInfo();
        String masterIp = nodeIpInfo.getMasterIp();
        if (isBlank(masterIp)) {
            throw new WrongArgumentException("k8s.get.master.ip.failed.not.uninstall");
        }
        String configPath = AnsibleConfigText.addNodeConfigFilePath(installDir);
        if (!execAnsiblePlaybook(masterIp, configPath, "add-node-minio-replicate.yml", ip)) {
            throw new WrongArgumentException("k8s.add.node.third.cluster.ansible.failed");
        }
        return true;
    }

    // ---------------------------------------------------------------- k8sMoreNode

    @Override
    public boolean k8sMoreNode() {
        boolean isK8sMoreNode = true;
        try {
            List<NodeSummaryVO> items = nodeRepository.listNodes();
            if (!items.isEmpty()) {
                String kubeletVersion = items.get(0).getKubeletVersion();
                if (!isBlank(kubeletVersion) && kubeletVersion.contains("k3s")) {
                    isK8sMoreNode = false;
                }
            }
            // 照抄 `detectNodeVersion:65-69`：`size() == 1` 也会把 isK8sMoreNode 置回 true。
            if (items.size() == 1) {
                isK8sMoreNode = true;
            }
        } catch (RuntimeException e) {
            log.error("获取集群节点信息失败，默认返回 k8s集群 版本，异常为:{}", e.getMessage());
        }
        return isK8sMoreNode;
    }

    // ---------------------------------------------------------------- unifyClusterEtcd

    @Override
    public boolean unifyClusterEtcd(String etcdNodeIp) {
        NodeIpInfoVO nodeIpInfo = getNodeIpInfo();
        for (Map.Entry<String, String> entry : nodeIpInfo.getNameIpMap().entrySet()) {
            String ip = entry.getKey();
            String hostName = entry.getValue();
            String path = mountPath;
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            path = path + "node-recovery/node-recovery.sh";
            // 厂商 `SshUtil.existRemoteFile(filePath)` = `find <dir>/<name>` 有输出。
            String found = run(ip, "find " + path);
            if (found == null || found.isEmpty()) {
                throw new WrongArgumentException("file.not.exist", path);
            }
            String command = String.format("bash -x %s %s", path, etcdNodeIp);
            String result = nodeRemotePort.exec(ip, command);
            log.info("ssh访问节点: {}, Ip: {} 统一集群节点etcd的结果: {}", hostName, ip, result);
        }
        return true;
    }

    // ---------------------------------------------------------------- ssh 组合（照抄 SshUtil）

    /** 厂商 `SshUtil.getRunCommand` + `getHostName/getNetName/getNetIpByNetName` 的 `replaceAll("\n","")`。 */
    private String run(String host, String command) {
        String out = nodeRemotePort.exec(host, command);
        return out == null ? null : out.replaceAll("\n", "");
    }

    /** 厂商 `SshUtil.updateHostName:792-806`。 */
    private boolean updateHostName(String host, String hostname) {
        nodeRemotePort.exec(host, "hostname " + hostname);
        String name = run(host, "hostname");
        if (name == null) {
            return false;
        }
        return name.equals(hostname);
    }

    /** 厂商 `SshUtil.addOrDeleteNodeInitConfig:808-822`（建目录 → 删旧文件 → `echo >`）。 */
    private boolean addOrDeleteNodeInitConfig(String host, String hostPath, String configFileName,
                                              String configContext) {
        try {
            createRemoteFolderIfNotExists(host, hostPath);
            String hostConfig = hostPath + "/" + configFileName;
            deleteRemoteFileIfExists(host, hostConfig);
            String createFileCommand = "echo \"" + configContext + "\" > " + hostConfig;
            log.debug("exec: {}", createFileCommand);
            nodeRemotePort.exec(host, createFileCommand);
            return true;
        } catch (RuntimeException e) {
            log.error("执行添加节点配置文件时出现异常:", e);
            return false;
        }
    }

    /** 厂商 `SshUtil.createRemoteFolderIfNotExists:884-...`。 */
    private void createRemoteFolderIfNotExists(String host, String folderPath) {
        String folderExists = run(host, "[ -d " + folderPath + " ] && echo true || echo false");
        if (!"true".equals(folderExists)) {
            nodeRemotePort.exec(host, "mkdir -p " + folderPath);
        } else {
            log.info("文件目录 {} 已存在，不需要创建", folderPath);
        }
    }

    /** 厂商 `SshUtil.deleteRemoteFileIfExists:899-...`。 */
    private void deleteRemoteFileIfExists(String host, String filePath) {
        String fileExists = run(host, "[ -f " + filePath + " ] && echo true || echo false");
        if ("true".equals(fileExists)) {
            nodeRemotePort.exec(host, "rm -f " + filePath);
        } else {
            log.info("旧添加节点文件 {} 不存在，不进行删除", filePath);
        }
    }

    /**
     * 厂商 `SshUtil.execAnsiblePlaybook:859-878`：先判 playbook 文件存在，
     * 再 `cd <ansiblePath> &&  source prepare-ansible.sh &&  ansible-playbook <yml> -i host/else  -i <configPath> --limit <ip>`。
     */
    private boolean execAnsiblePlaybook(String host, String configPath, String playbookName, String ip) {
        String ansiblePath = installDir;
        if (!ansiblePath.endsWith("/")) {
            ansiblePath = ansiblePath + "/";
        }
        if (!fileExists(host, ansiblePath + playbookName)) {
            log.info("在安装包目录下不存在将要执行卸载节点脚本{}文件,无法进行卸载", playbookName);
            return false;
        }
        String command = "cd " + installDir + " &&  source " + PREPARE_ANSIBLE_SHELL + " &&  ansible-playbook "
                + playbookName + " -i host/else  -i " + configPath + " --limit " + ip;
        log.debug("exec: {}", command);
        nodeRemotePort.exec(host, command);
        return true;
    }

    /** 厂商 `SshUtil.fileExists:880-883`。 */
    private boolean fileExists(String host, String filePath) {
        String response = run(host, "[ -f " + filePath + " ] && echo true || echo false");
        return "true".equals(response);
    }

    /**
     * 厂商 `AnsibleConfigConstant.appendVipConfig:104-122`：在 `ssh.host` 上 `cat /data/paas/vip/config.json`。
     * 读失败 ⇒ 返回空串（厂商也是 catch 后只记日志、不输出该段）。
     */
    private String readVipSection() {
        try {
            return AnsibleConfigText.vipSection(nodeRemotePort.exec(sshHost, "cat " + VIP_CONFIG_JSON_PATH));
        } catch (RuntimeException e) {
            log.error("获取VIP配置文件失败：" + VIP_CONFIG_JSON_PATH, e);
            return "";
        }
    }

    // ---------------------------------------------------------------- 节点 ip 信息

    /**
     * 厂商 `K8sObjectUtil.getNodeIpInfo:112-146`：
     * {@code masterIp} 取 label {@code control-plane=first} 节点的 `status.addresses[0]`，
     * {@code nameIpMap} 是 InternalIP → Hostname；两个地址必须都非空白才入 map。
     */
    private NodeIpInfoVO getNodeIpInfo() {
        String masterIp = null;
        Map<String, String> nameIpMap = new HashMap<>();
        for (NodeSummaryVO node : nodeRepository.listNodes()) {
            Map<String, String> labels = node.getLabels() == null ? new HashMap<>() : node.getLabels();
            String controlPlane = labels.get(INITIAL_FIRST_CONTROL_PLANE_LABEL_KEY);
            if (INITIAL_FIRST_CONTROL_PLANE_LABEL_VALUE.equals(controlPlane)
                    && masterIp == null && node.getAddresses() != null && !node.getAddresses().isEmpty()) {
                masterIp = node.getAddresses().get(0);
            }
            if (!isBlank(node.getInternalIp()) && !isBlank(node.getHostname())) {
                nameIpMap.put(node.getInternalIp(), node.getHostname());
            }
        }
        return new NodeIpInfoVO(masterIp, nameIpMap);
    }

    /** 厂商用的是 `StringUtils.isBlank`（commons-lang3 在 domain 不可用，这里等价实现）。 */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
