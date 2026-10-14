package com.csm.paas.core.domain.node.service;

import com.csm.paas.core.domain.node.model.valobj.NodeAddFormVO;
import com.csm.paas.core.domain.node.model.valobj.NodeConnectInfoVO;

/**
 * 公共 API - 节点管理（厂商 `controller/pub/PublicNodeManagerController.java`）。
 *
 * <p>路径 ⇄ 厂商方法：
 * <ul>
 *   <li>{@code GET  /v1/pub/testConnect}    → {@code testConnectServer:41}（缺 ip 校验那步在控制器）；</li>
 *   <li>{@code GET  /v1/pub/existNodeName}  → {@code existNodeName:52}；</li>
 *   <li>{@code POST /v1/pub/uninstallNode}  → {@code uninstallNode:65}；</li>
 *   <li>{@code POST /v1/pub/initHost}       → {@code addNodeInit:75}；</li>
 *   <li>{@code POST /v1/pub/setDockerConfig}→ {@code addNodeInstallDockerEnv:85}；</li>
 *   <li>{@code POST /v1/pub/finallyAddNode} → {@code finallyAddNode:95}；</li>
 *   <li>{@code POST /v1/pub/addFdfsByNode}  → {@code addFdfsByNode:105}（走 `configFileService`）。</li>
 * </ul>
 *
 * <p><b>类 C</b>：除 {@code existNodeName} 外全部依赖 SSH/ansible（无节点可连，代码齐全但不做在线验证）。
 */
public interface INodeManagerService {

    /** 新增节点 ip 连通性测试（厂商 `SoftwareService.testConnectServer:4695-4791`）。 */
    NodeConnectInfoVO testConnectServer(String ip);

    /** 节点名是否已被集群使用（厂商 `SoftwareService.existNodeName:4793-4813`）。 */
    boolean existNodeName(String name);

    /** 卸载节点（厂商 `SoftwareService.uninstallNode:4815-4895`，跑 `uninstall-node.yml`）。 */
    boolean uninstallNode(String ip, String nodeName);

    /** 新增节点第一步：初始化配置（厂商 `addNodeInitHost:4897-5020`，跑 `add-node-prepare.yml`）。 */
    boolean addNodeInitHost(NodeAddFormVO form);

    /** 新增节点第二步：配 docker 环境（厂商 `addNodeInstallDockerEnv:5022-5101`，跑 `add-node-docker.yml`）。 */
    boolean addNodeInstallDockerEnv(NodeAddFormVO form);

    /** 新增节点第三步：加入集群（厂商 `finallyAddNode:5103-5182`，跑 `add-node-kubernetes.yml`）。 */
    boolean finallyAddNode(NodeAddFormVO form);

    /** 新增节点后刷新 fdfs/minio 复制配置（厂商 `configFileService:5184-5256`，跑 `add-node-minio-replicate.yml`）。 */
    boolean configFileService(String ip);

    /**
     * 集群是否为"更多节点"（厂商 `impl/NodeVersionServiceImpl.k8sMoreNode:45` + `detectNodeVersion:55-83`）。
     *
     * <p>照抄它的**怪点**：默认 {@code true}；首个节点 kubeletVersion 含 `k3s` ⇒ 置 false；
     * 然后"节点数 == 1 ⇒ 置 true"。也就是说**单节点也是 true**，判据并不是"节点数 > 1"。
     */
    boolean k8sMoreNode();

    /**
     * 把集群的 etcd 统一到目标 etcd ip（厂商 `SoftwareService.unifyClusterEtcd:5351-5423`，
     * 接口 {@code GET /v1/pub/unify/etcd}）。
     *
     * <p>逐节点 SSH：先 `find <mount.path>/node-recovery/node-recovery.sh` 判存在
     * （不存在抛 {@code file.not.exist} 带路径），再执行
     * {@code bash -x <path> <etcdNodeIp>}；单节点连不上抛 `ssh.connect.failed.no.arg`。
     */
    boolean unifyClusterEtcd(String etcdNodeIp);
}
