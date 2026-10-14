package com.csm.paas.core.domain.node.service;

import java.util.List;

/**
 * 服务器运维（厂商 `controller/pub/PublicFdfsController.java` 尾部几条 + `service/FileService.java`）。
 *
 * <p>路径 ⇄ 厂商方法：
 * <ul>
 *   <li>{@code POST /v1/pub/restartFdfs}    → {@code PublicFdfsController.restartIpFastdfs:207} ⇒ {@code FileService.restartIpFastdfs};</li>
 *   <li>{@code POST /v1/pub/connect}        → {@code PublicFdfsController.connectServerTest:215} ⇒ {@code FileService.connectServerTest};</li>
 *   <li>{@code POST /v1/pub/restartServer}  → {@code PublicFdfsController.restartServer:223} ⇒ {@code FileService.restartServer};</li>
 *   <li>{@code POST /v1/pub/shutdownServer} → {@code PublicFdfsController.shutdownServer:231} ⇒ {@code FileService.shutdownServer}.</li>
 * </ul>
 *
 * <p><b>类 C</b>：全部走 SSH（`reboot` / `shutdown -h now` / `systemctl restart fdfs_*`），本环境无节点可连。
 */
public interface IServerMaintenanceService {

    /**
     * 重启这些节点上的 fdfs tracker/storaged。
     * <p>厂商 `FileService.restartIpFastdfs:1360-1405`：**ip 列表为空时抛 400**
     * （`fdfs.no.restart.ip.specified`），连接失败抛 `ssh.connect.failed`（带 ip），
     * 但**命令本身执行失败只记日志**（`:1388-1393`），最终返回 true。
     */
    boolean restartIpFastdfs(List<String> ipList);

    /**
     * 连通性测试。
     * <p>厂商 `FileService.connectServerTest:1408-1444`：列出全部节点的 InternalIP，
     * 每个 ip 丢进线程池执行 {@code ConnectTask}（socket 探活 + WebSocket 推送），
     * **只在线程池提交失败时返回 false**，最终返回 true。
     * <p>本实现保留节点枚举与"提交失败即 false"的语义；WebSocket 推送由触发层负责，此处只做 TCP 探活。
     */
    boolean connectServerTest();

    /** 重启服务器（`reboot`）；厂商 `FileService.restartServer:1462-1478` + `reStart:1793-1833`。 */
    boolean restartServer(String master, String worker);

    /** 关闭服务器（`shutdown -h now`）；厂商 `FileService.shutdownServer:1446-1460` + `shutdown:1751-1791`。 */
    boolean shutdownServer(String master, String worker);
}
