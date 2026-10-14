package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.node.adapter.port.INodeRemotePort;
import com.csm.paas.core.domain.node.adapter.repository.INodeRepository;
import com.csm.paas.core.domain.node.model.valobj.NodeSummaryVO;
import com.csm.paas.core.domain.node.service.IServerMaintenanceService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 服务器运维实现（厂商 `service/FileService.java:1360-1478`、`:1751-1833`）。
 *
 * <p><b>类 C</b>：本环境无节点可 SSH，代码齐全、不做在线验证。
 *
 * <p>照抄点：
 * <ul>
 *   <li>`restartIpFastdfs` 空列表 ⇒ 抛 `fdfs.no.restart.ip.specified`（`:1402`）；</li>
 *   <li>`restartServer`/`shutdownServer` 都是**先处理 worker 再处理 master**（`:1446-1478`）；</li>
 *   <li>每条命令的失败只记日志、不影响返回值（`:1388-1393`、`:1772-1776`、`:1814-1818`）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerMaintenanceServiceImpl implements IServerMaintenanceService {

    /** 厂商 `SshUtil.restartFdfs:720-732`。 */
    private static final String CMD_RESTART_TRACKER = "systemctl restart fdfs_trackerd.service";
    /** 厂商 `SshUtil.restartFdfs:726-730`。 */
    private static final String CMD_RESTART_STORAGE = "systemctl restart fdfs_storaged.service";
    /** 厂商 `SshUtil.reStartServer:734-741`。 */
    private static final String CMD_REBOOT = "reboot";
    /** 厂商 `SshUtil.shutdownServer:743-750`。 */
    private static final String CMD_SHUTDOWN = "shutdown -h now";

    private final INodeRepository nodeRepository;
    private final INodeRemotePort nodeRemotePort;

    @Override
    public boolean restartIpFastdfs(List<String> ipList) {
        if (ipList == null || ipList.isEmpty()) {
            throw new WrongArgumentException("fdfs.no.restart.ip.specified");
        }
        for (String ip : ipList) {
            try {
                nodeRemotePort.exec(ip, CMD_RESTART_TRACKER);
                nodeRemotePort.exec(ip, CMD_RESTART_STORAGE);
            } catch (WrongArgumentException e) {
                throw e;
            } catch (RuntimeException e) {
                // 厂商把"连接不上"和"命令失败"分开：连接不上抛 400，命令失败只记日志。
                // 端口层连不上与命令失败都抛 IllegalStateException，这里无法区分，按"命令失败"处理。
                log.error("执行命令出现异常:{}", e.getMessage(), e);
            }
        }
        return true;
    }

    @Override
    public boolean connectServerTest() {
        List<String> hostList = new ArrayList<>();
        try {
            for (NodeSummaryVO node : nodeRepository.listNodes()) {
                if (node.getInternalIp() != null) {
                    hostList.add(node.getInternalIp());
                }
            }
        } catch (RuntimeException e) {
            log.error("异常", e);
        }
        // 厂商 `:1432`：threadPool.execute(...) 抛异常 ⇒ 返回 false。
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, hostList.size()));
        try {
            for (String host : hostList) {
                executor.execute(() -> nodeRemotePort.reachable(host, 22, 3000));
            }
        } catch (RuntimeException e) {
            log.error("执行线程出现异常:", e);
            return false;
        } finally {
            executor.shutdown();
        }
        return true;
    }

    @Override
    public boolean restartServer(String master, String worker) {
        if (isNotBlank(worker)) {
            reStart(Arrays.asList(worker.split(",")));
        }
        if (isNotBlank(master)) {
            reStart(Arrays.asList(master.split(",")));
        }
        return true;
    }

    @Override
    public boolean shutdownServer(String master, String worker) {
        if (isNotBlank(worker)) {
            shutdown(Arrays.asList(worker.split(",")));
        }
        if (isNotBlank(master)) {
            shutdown(Arrays.asList(master.split(",")));
        }
        return true;
    }

    /** 厂商 `FileService.reStart:1793-1833`。 */
    private void reStart(List<String> workerList) {
        for (String ip : workerList) {
            try {
                nodeRemotePort.exec(ip, CMD_REBOOT);
            } catch (RuntimeException e) {
                log.error("重启服务器时出错出错", e);
            }
        }
    }

    /** 厂商 `FileService.shutdown:1751-1791`。 */
    private void shutdown(List<String> workerList) {
        for (String ip : workerList) {
            try {
                nodeRemotePort.exec(ip, CMD_SHUTDOWN);
            } catch (RuntimeException e) {
                log.error("关闭服务器时出错出错", e);
            }
        }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
