package com.csm.paas.core.domain.cluster.adapter.port;

import java.util.function.BiConsumer;

/**
 * 集群事件端口：把 K8s 的 watch 事件交给调用方（实现放 infrastructure/adapter/port，只调 gateway）。
 * <p>回调参数是 `(kind, 事件体 JSON)`，事件体形状与厂商一致：`{type, object}`，
 * type ∈ ADDED/MODIFIED/DELETED/ERROR。
 */
public interface IK8sEventPort {

    void startWatch(String namespace, BiConsumer<String, String> onEvent);

    void stopWatch();
}
