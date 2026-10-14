package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.node.model.valobj.NodeAddFormVO;
import com.csm.paas.core.domain.node.model.valobj.NodeIpInfoVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 厂商 ansible inventory 文本拼装（逐字照抄 `constant/AnsibleConfigConstant.java`）。
 *
 * <p>照抄点（含怪点，全部保留）：
 * <ul>
 *   <li>{@code ALL_VARS_MAP} 里 {@code "kubernetes_install_dir "} 与 {@code "remote_package_base "}
 *       **键名尾部带空格**（`:227`、`:259`）——原样保留；</li>
 *   <li>{@code docker_registry_nginx_url} 的 Jinja 模板原样保留（`:232`）；</li>
 *   <li>{@code ntp_network = "192.13" + "8.0.0"}、{@code ntp_network_mask = "255.25" + "5.252.0"}
 *       这种"拼出来"的写法原样保留（`:236-239`）；</li>
 *   <li>{@code [k8s-worker]} 段落里写的是 {@code <ip> hostname=<hostname> label_nvidia=false}
 *       —— 键是 **ip**、值是 hostname（`:129`，与 {@code nameIpMap} 的方向一致）。</li>
 * </ul>
 *
 * <p>{@code appendVipConfig} 厂商用 Jackson 读 `/data/paas/vip/config.json`
 * （`:104-122`，读失败只记日志、**不**输出该段）。域层禁用 Jackson，这里改成
 * SSH `cat` 后用正则取 {@code vip}/{@code defaultInterface} 两个标量，语义等价。
 */
final class AnsibleConfigText {

    /** 厂商 `AnsibleConfigConstant.PRO_IGNORE`（`:33`）。 */
    static final String PRO_IGNORE = "proIgnore";
    /** 厂商 `AnsibleConfigConstant.ADD_NODE_CONFIG`（`:34`）。 */
    static final String ADD_NODE_CONFIG = "addNode";

    private static final String DATA_PAAS_VIP_CONFIG_JSON = "/data/paas/vip/config.json";

    private static final List<String> DOCKER_CONFIG_LIST = new ArrayList<>(2);
    private static final List<String> NET_SERVER_CONFIG_LIST = new ArrayList<>(1);
    private static final Map<String, String> ALL_VARS_MAP = new LinkedHashMap<>(16);
    private static final Map<String, String> ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH = new LinkedHashMap<>(14);
    private static final String BASE_CONFIG;

    static {
        DOCKER_CONFIG_LIST.add("k8s-master");
        DOCKER_CONFIG_LIST.add("k8s-worker");
        NET_SERVER_CONFIG_LIST.add("k8s-master");
        ALL_VARS_MAP.put("cluster_allinone", "false");
        ALL_VARS_MAP.put("install_master_fdfs", "false");
        ALL_VARS_MAP.put("fdfs_group", "group1");
        ALL_VARS_MAP.put("system_repo", "system_containers");
        ALL_VARS_MAP.put("kubernetes_install_dir ", "/etc/kubernetes");
        ALL_VARS_MAP.put("distributed", "false");
        ALL_VARS_MAP.put("harbor_port", "1080");
        ALL_VARS_MAP.put("minio_user", "admin");
        ALL_VARS_MAP.put("minio_passwd", "12345678");
        ALL_VARS_MAP.put("docker_registry_nginx_url",
                "{% if (groups['k8s-worker'] | length) == 0 %}{{groups['k8s-master'][0]}}{%else%}"
                        + "{{groups['gaop-vip-addresses'][0].split(':')[0]}}{%endif%}:{{harbor_port}}");
        ALL_VARS_MAP.put("registry_user", "system_containers");
        ALL_VARS_MAP.put("multicast", "true");
        ALL_VARS_MAP.put("default_ipv4_address",
                "{% if 'ansible_'~default_interface in vars %}{{vars['ansible_'~default_interface].ipv4.address}}"
                        + "{% elif 'address' in ansible_default_ipv4 %}{{ansible_default_ipv4.address}}"
                        + "{%else%}{{ansible_all_ipv4_addresses.0}}{%endif%}");
        String ntpNetworkLast = "8.0.0";
        String ntpNetworkMaskLast = "5.252.0";
        ALL_VARS_MAP.put("ntp_network", "192.13" + ntpNetworkLast);
        ALL_VARS_MAP.put("ntp_network_mask", "255.25" + ntpNetworkMaskLast);
        ALL_VARS_MAP.put("diagnostic_path", "/data/gaop-diagnostic");

        StringBuilder sb = new StringBuilder();
        sb.append("[docker]\n\n").append("[docker:children]\n");
        for (String s : DOCKER_CONFIG_LIST) {
            sb.append(s).append("\n");
        }
        sb.append("\n[ntp-server]\n\n").append("[ntp-server:children]\n");
        for (String s : NET_SERVER_CONFIG_LIST) {
            sb.append(s).append("\n");
        }
        sb.append("\n[all:vars]\n");
        for (Map.Entry<String, String> e : ALL_VARS_MAP.entrySet()) {
            sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
        }
        BASE_CONFIG = sb.toString();

        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("fdfs_path", "/dfs");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("remote_dir", "");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("dest_path", "/gaop");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("remote_package_path", "/packages/packages-deb-kylin");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("remote_package_base ", "/packages");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("etcd_storge", "/etcd");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("etcd_bakdir", "/bak/etcd");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("check_node_status_script_bakdir", "/vip-log");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("working_dir", "");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("docker_dir", "/basic");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("docker_dir_tools", "/basic/tools");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("cni_plugin", "/network");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("docker_root_dir", "/docker");
        ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.put("image_plugin", "/basic/image");
    }

    private AnsibleConfigText() {
    }

    /** 厂商 `getConfigPath:50-55`。 */
    static String configPath(String installDir) {
        if (!installDir.endsWith("/")) {
            installDir = installDir + "/";
        }
        return installDir + PRO_IGNORE;
    }

    /** 厂商 `getAddNodeConfigFilePath:42-44`。 */
    static String addNodeConfigFilePath(String installDir) {
        return configPath(installDir) + "/" + ADD_NODE_CONFIG;
    }

    /** 厂商 `getAddNodeConfig:61-63` ⇒ `getConfig(nodeIpInfo, addNodeInfo, true, false, ...)`。 */
    static String addNodeConfig(NodeIpInfoVO nodeIpInfo, NodeAddFormVO addNodeInfo, String vipSection,
                               String ansiblePassword, String mountPath) {
        return config(nodeIpInfo, addNodeInfo, true, false, vipSection, ansiblePassword, mountPath);
    }

    /**
     * 厂商 `getMasterAddNodeConfig:65-67` —— **与 {@link #addNodeConfig} 逐字相同**
     * （两个都走 `getConfig(..., true, false, ...)`），照抄不合并。
     */
    static String masterAddNodeConfig(NodeIpInfoVO nodeIpInfo, NodeAddFormVO addNodeInfo, String vipSection,
                                      String ansiblePassword, String mountPath) {
        return config(nodeIpInfo, addNodeInfo, true, false, vipSection, ansiblePassword, mountPath);
    }

    /** 厂商 `getMasterUninstallNodeConfig:69-71`：把要卸载的 ip/hostname 包成同一个表单。 */
    static String masterUninstallNodeConfig(NodeIpInfoVO nodeIpInfo, String addNodeIp, String addNodeHostName,
                                            String vipSection, String ansiblePassword, String mountPath) {
        return config(nodeIpInfo, NodeAddFormVO.builder().ip(addNodeIp).hostname(addNodeHostName).build(),
                true, false, vipSection, ansiblePassword, mountPath);
    }

    /**
     * 厂商 `getConfig:73-83`。段序与厂商一致：master → worker → 新节点 → vip → base。
     *
     * @param vipSection `appendVipConfig` 的产物（厂商在方法内部 SSH 读 vip config.json，
     *                   域层把它提到调用方，见 {@link #vipSection(String)}）
     */
    static String config(NodeIpInfoVO nodeIpInfo, NodeAddFormVO addNodeInfo, boolean needMaster,
                         boolean needExistedWorker, String vipSection, String ansiblePassword, String mountPath) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> nameIpMap = nodeIpInfo.getNameIpMap();
        String masterIp = nodeIpInfo.getMasterIp();
        appendMasterConfig(needMaster, sb, nameIpMap, masterIp);
        appendWorkerConfig(needExistedWorker, sb, nameIpMap, masterIp);
        appendNewNodeConfig(sb, addNodeInfo);
        sb.append(vipSection == null ? "" : vipSection);
        appendBaseConfig(sb, ansiblePassword, addNodeInfo, mountPath);
        return sb.toString();
    }

    /** 厂商 `appendMasterConfig:134-141`（`needMaster=false` 时只剩 `[k8s-master]\n\n`）。 */
    private static void appendMasterConfig(boolean needMaster, StringBuilder sb,
                                           Map<String, String> nameIpMap, String masterIp) {
        sb.append("[k8s-master]\n");
        if (needMaster) {
            String masterHostName = nameIpMap.get(masterIp);
            sb.append(masterIp).append(" hostname=").append(masterHostName).append(" label_nvidia=false\n");
        }
        sb.append("\n");
    }

    /** 厂商 `appendWorkerConfig:124-132`（本类调用点全是 `needExistedWorker=false`）。 */
    private static void appendWorkerConfig(boolean needExistedWorker, StringBuilder sb,
                                           Map<String, String> nameIpMap, String masterIp) {
        sb.append("[k8s-worker]\n");
        if (needExistedWorker) {
            for (Map.Entry<String, String> entry : nameIpMap.entrySet()) {
                if (entry.getKey().equals(masterIp)) {
                    continue;
                }
                sb.append(entry.getKey()).append(" hostname=").append(entry.getValue())
                        .append(" label_nvidia=false\n");
            }
        }
    }

    /** 厂商 `appendNewNodeConfig:143-149`。 */
    private static void appendNewNodeConfig(StringBuilder sb, NodeAddFormVO addNodeInfo) {
        if (addNodeInfo != null) {
            sb.append(addNodeInfo.getIp()).append(" hostname=").append(addNodeInfo.getHostname());
            sb.append(" label_nvidia=").append(Boolean.TRUE.equals(addNodeInfo.getLabelNvidia())).append("\n");
        }
        sb.append("\n");
    }

    /** 厂商 `appendBaseConfig:151-162`。 */
    private static void appendBaseConfig(StringBuilder sb, String ansiblePassword, NodeAddFormVO addNodeInfo,
                                         String mountPath) {
        sb.append(BASE_CONFIG).append("ansible_password=").append(ansiblePassword).append("\n");
        if (mountPath.endsWith("/")) {
            mountPath = mountPath.substring(0, mountPath.length() - 1);
        }
        for (Map.Entry<String, String> entry : ALL_VARS_MAP_NEED_APPEND_MOUNT_PATH.entrySet()) {
            sb.append(entry.getKey()).append("=").append(mountPath).append(entry.getValue()).append("\n");
        }
        if (addNodeInfo != null && addNodeInfo.getNetWorkInterface() != null) {
            sb.append("default_interface=").append(addNodeInfo.getNetWorkInterface()).append("\n");
        }
    }

    /**
     * 厂商 `appendVipConfig:104-122` 的等价实现（Jackson 换成正则），
     * 触发 `[gaop-vip-addresses]` 段：`<vip去掩码> interface=<defaultInterface>`。
     * 读文件失败 ⇒ 整段不输出（厂商也是 catch 后只记日志）。
     *
     * @param configJson SSH `cat /data/paas/vip/config.json` 的原文；null/空白表示读失败
     */
    static String vipSection(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            return "";
        }
        String vipWithMask = scalar(configJson, "vip");
        if (vipWithMask == null) {
            return "";
        }
        String[] split = vipWithMask.split("/");
        String vip = split.length > 1 ? split[0] : vipWithMask;
        String defaultInterface = scalar(configJson, "defaultInterface");
        return "[gaop-vip-addresses]\n" + vip + " " + "interface=" + defaultInterface + "\n";
    }

    private static String scalar(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
