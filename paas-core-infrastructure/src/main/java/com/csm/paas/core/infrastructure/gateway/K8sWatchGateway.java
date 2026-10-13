package com.csm.paas.core.infrastructure.gateway;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * fabric8 的 K8s watch 网关。只做"监听并把事件交给回调"，不含业务判断。
 * <p>事件体与厂商一致：`{type, object}`，type ∈ ADDED/MODIFIED/DELETED/ERROR（fabric8 的 {@link Watcher.Action} 同名）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class K8sWatchGateway {

    private final KubernetesClient kubernetesClient;

    /** 启动对 Service / Deployment / Pod 的 watch；回调 (kind, 事件体 JSON)。 */
    public Map<String, Watch> watchWorkloads(String namespace, BiConsumer<String, String> onEvent) {
        Map<String, Watch> watches = new LinkedHashMap<>();
        watches.put("Service", watch(
                kubernetesClient.services().inNamespace(namespace).watch(new JsonWatcher<Service>("Service", onEvent))));
        watches.put("Deployment", watch(
                kubernetesClient.apps().deployments().inNamespace(namespace).watch(new JsonWatcher<Deployment>("Deployment", onEvent))));
        watches.put("Pod", watch(
                kubernetesClient.pods().inNamespace(namespace).watch(new JsonWatcher<Pod>("Pod", onEvent))));
        log.info("已启动 namespace:{} 的 Service/Deployment/Pod watch", namespace);
        return watches;
    }

    private Watch watch(Watch watch) {
        return watch;
    }

    /** 把 K8s 事件转成厂商的 `{type, object}` JSON。 */
    private static final class JsonWatcher<T extends HasMetadata> implements Watcher<T> {

        private final String kind;
        private final BiConsumer<String, String> onEvent;

        JsonWatcher(String kind, BiConsumer<String, String> onEvent) {
            this.kind = kind;
            this.onEvent = onEvent;
        }

        @Override
        public void eventReceived(Action action, T resource) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", action.name());
                payload.put("object", Serialization.jsonMapper().readValue(
                        Serialization.asJson(resource), Map.class));
                onEvent.accept(kind, Serialization.asJson(payload));
            } catch (Exception e) {
                log.warn("构造 {} 事件失败: {}", kind, e.getMessage());
            }
        }

        @Override
        public void onClose(WatcherException cause) {
            if (cause != null) {
                log.warn("{} watch 关闭: {}", kind, cause.getMessage());
            }
        }
    }

    /** 供编译期类型检查用（fabric8 的具体资源类型）。 */
    @SuppressWarnings("unused")
    private static final Class<?>[] RESOURCE_TYPES = {Service.class, Deployment.class, Pod.class};
}
