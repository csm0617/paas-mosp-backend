package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.cluster.adapter.port.IK8sEventPort;
import com.csm.paas.core.infrastructure.gateway.K8sWatchGateway;
import io.fabric8.kubernetes.client.Watch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.BiConsumer;

/** Port 实现：只调 gateway，持有 watch 句柄。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class K8sEventPort implements IK8sEventPort {

    private final K8sWatchGateway k8sWatchGateway;

    private Map<String, Watch> watches;

    @Override
    public void startWatch(String namespace, BiConsumer<String, String> onEvent) {
        if (watches != null) {
            return;
        }
        try {
            watches = k8sWatchGateway.watchWorkloads(namespace, onEvent);
            log.info("K8s watch 已启动: {}", watches.keySet());
        } catch (Exception e) {
            watches = null;
            log.warn("K8s watch 启动失败: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void stopWatch() {
        if (watches == null) {
            return;
        }
        watches.values().forEach(w -> {
            try {
                w.close();
            } catch (Exception ignored) {
                // 关闭失败无需处理
            }
        });
        watches = null;
    }
}
