package com.csm.paas.core.domain.node.adapter.port;

/**
 * 面向"任意节点 ip"的 SSH 执行端口。
 *
 * <p>为什么不能直接用 {@code ISshPort}：那个端口的目标主机固定来自配置
 * （`gaop.ssh.host`），而厂商这些接口的目标是**传进来的 ip**——比如
 * `SoftwareService.testConnectServer:4709` 是 `new SshUtil(user, pass, ip)`、
 * `uninstallNode:4826` 连的是 `nodeIpInfo.getMasterIp()`。
 * 所以这里按"主机"参数化，命令内容仍由域层照抄厂商拼。
 *
 * <p>厂商出处：`utils/SshUtil.java` 的
 * `getRunCommand:379`（ChannelExec + stdout）、
 * `connectSessionAndSend:774`（连不上抛 `ssh.connect.failed.no.arg`）。
 *
 * <p><b>类 C</b>：本环境没有可 SSH 的节点 ⇒ 代码齐全、不做在线验证。
 */
public interface INodeRemotePort {

    /**
     * 指定主机端口的一次性 TCP 探活。
     *
     * <p>照抄厂商 `SoftwareService.testConnectServer:4700-4707`：先
     * `ClientGlobal.getSocket(new InetSocketAddress(ip, 22))`（FastDFS 客户端的 socket 工具，
     * 这里用等价的一次 connect 实现），成功才 `/api/v1/pub/testConnect` 继续走 SSH。
     *
     * @param host      目标 ip
     * @param port      端口（厂商写死 22）
     * @param timeoutMs 连接超时（厂商 `ClientGlobal.setG_connect_timeout(3000)`）
     * @return 能连上为 true；任何 IO 异常为 false（厂商捕获后直接 `return connectInfo`）
     */
    boolean reachable(String host, int port, int timeoutMs);

    /**
     * 在 {@code host} 上执行一条命令并返回 stdout（厂商 `SshUtil.getRunCommand:379`）。
     *
     * <p>厂商实现里命令失败/连不上时 `getRunCommand` 返回 null，调用方多半不判空
     * （例如 `getNetIpByNetName:651` 直接 `.replaceAll` 会 NPE）——这里**照抄**：连不上抛
     * `IllegalStateException`，命令本身失败仍返回 stdout 原文（可能为空串）。
     */
    String exec(String host, String command);
}
