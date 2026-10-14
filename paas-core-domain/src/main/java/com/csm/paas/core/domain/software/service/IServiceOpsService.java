package com.csm.paas.core.domain.software.service;

/**
 * 服务域的**运维动作**（破坏性）：节点调度、重启 kube-system 基础组件、删 Pod 容器。
 *
 * <p>三个端点都照抄厂商 `ServiceController`，且都**会真的改动集群**：
 * <ul>
 *   <li>`PUT /v1/service/schedulingNode?name&type`（`:3087-3165`）——SSH 到 master 节点执行
 *       `kubectl cordon`（type=0，停止调度）/ `kubectl uncordon`（type=1，恢复调度）；</li>
 *   <li>`POST /v1/service/reStartBaseService?podName`（`:3313-3375`）——SSH 到 master 执行
 *       `kubectl delete pod <podName> -n kube-system --force --grace-period=0`；</li>
 *   <li>`DELETE /v1/service/servicePods`（`:2327-2343`）——直接删 Pod（body 里带 namespace/force）。</li>
 * </ul>
 *
 * <p><b>类 C：本环境不可验证</b>——前两个依赖 SSH 到节点，本环境没有可连的节点；
 * 第三个虽是纯 K8s 调用，但属破坏性操作，本轮不做在线实验（只用离线单测钉住报文与分支）。
 */
public interface IServiceOpsService {

    /**
     * 节点调度（厂商 `schedulingNode:3087-3165`）。
     *
     * <p>逐行照抄的分支：
     * <ol>
     *   <li>列全部节点；**列失败 ⇒ 直接返回 `false`**（HTTP 200 + `data:false`，不报错）；</li>
     *   <li>把所有节点名收进 `ipList`（厂商变量名叫 ipList，装的却是节点名——照抄），
     *       并取"**最后一个**带 `node-role.kubernetes.io/&lt;nodeRole&gt;` 标签的节点的
     *       `status.addresses[0].address`"当 master IP；</li>
     *   <li>`name` 不在节点名单里 ⇒ 400 `service.node.name.not.found`（带 name）；</li>
     *   <li>SSH 到 master IP；**连接失败 ⇒ 返回 `false`**（不抛）；</li>
     *   <li>`type == 0` ⇒ `kubectl cordon <name>`，命令抛错 ⇒ 400 `service.stop.schedule.failed`（带 name）；
     *       `type == 1` ⇒ `kubectl uncordon <name>`，命令抛错 ⇒ 400 `service.start.schedule.failed`（带 name）。
     *       两个 `if` 在厂商源码里**互相独立**，所以其它 type 值既不执行命令、也照样走到最后返回 `true`；</li>
     *   <li>收尾关连接，返回 `true`。</li>
     * </ol>
     *
     * <p><b>破坏性</b>：会真的把节点标记为不可调度/恢复调度。
     */
    boolean schedulingNode(String name, Integer type);

    /**
     * 重启 kube-system 基础服务组件（厂商 `reStartBaseService:3313-3375`）。
     *
     * <p>与 {@link #schedulingNode} 同样的"列节点 → 找 master IP → SSH"三步，
     * 但**不校验** `podName` 是否在节点名单里；连接失败同样返回 `false`。
     * 命令是 `kubectl delete pod <podName> -n kube-system --force --grace-period=0`，
     * 失败 ⇒ 400 `ssh.delete.base.service.pod.failed`（带 podName）。
     *
     * <p><b>破坏性</b>：强删 kube-system 里的 pod。
     */
    boolean restartBaseService(String podName);

    /**
     * 批量删 Pod（厂商 `deleteServicePods:2327-2343`）。
     *
     * <p>逐个删；`force` 为 true ⇒ `gracePeriodSeconds = 0`，否则不带该字段。
     * 任一删除失败 ⇒ 500 `service.delete.pod.failed`（带 podName，厂商还把响应体塞进消息里）。
     * <p>厂商**不校验** `namespace`/`podNames` 是否为空。
     * <p><b>破坏性</b>：直接删容器。
     */
    boolean deletePods(String namespace, java.util.List<String> podNames, Boolean force);
}
