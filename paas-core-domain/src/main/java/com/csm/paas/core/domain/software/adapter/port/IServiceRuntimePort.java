package com.csm.paas.core.domain.software.adapter.port;

import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;

import java.util.List;

/**
 * 运行态服务的只读入口（远程/集群读取）。
 * <p>实现必须放在 {@code infrastructure/adapter/port}，并且只能调用 {@code gateway/}。
 */
public interface IServiceRuntimePort {

    List<ServiceRuntimeEntity> queryServices(String namespace);

    /**
     * 集群里**所有节点 label** 的键与去重值（厂商 `ServiceController.getLabels:964-990`）：
     * 遍历 `listNode` 的每个 `metadata.labels`，按 label 键聚合出值集合。
     * <p><b>顺序不可依赖</b>：厂商是 `HashMap` 聚合再 `forEach`，两次调用顺序都可能不同——
     * 所以对拉时只能比集合，不能比数组顺序。
     */
    java.util.Map<String, java.util.List<String>> queryNodeLabels();

    /**
     * 给 Deployment 打 JSON patch，替换 `spec.template.metadata.annotations.gaop/serviceLabel`
     * （厂商 `ServiceController.updateServiceName:1536-1553` 的第一条路径）。失败时抛异常，由领域层决定是否回退 StatefulSet。
     */
    void patchDeploymentServiceLabel(String namespace, String serviceKey, String name);

    /** 同上，但打在 StatefulSet 上（厂商失败回退路径）。 */
    void patchStatefulSetServiceLabel(String namespace, String serviceKey, String name);

    // ---------- hostname（厂商 PUT /v1/service/hostName）----------

    /** Deployment 是否存在（厂商只在"读不到"时回退 StatefulSet，**不是**"hostname 为空时"）。 */
    boolean deploymentExists(String namespace, String serviceName);

    /** 读 Deployment 的 hostname；Deployment 存在但没有该字段时为 null。 */
    String deploymentHostname(String namespace, String serviceName);

    void patchDeploymentHostname(String namespace, String serviceName, String hostname);

    String statefulSetHostname(String namespace, String serviceName);

    void patchStatefulSetHostname(String namespace, String serviceName, String hostname);

    /** 设置 Deployment 的 `priorityClassName`（厂商 `POST /v1/service/priority`）。 */
    void patchDeploymentPriorityClassName(String namespace, String serviceName, String level);

    /** strategic-merge-patch 副本数到 Deployment；**对象不存在返回 null**（厂商据此回退 StatefulSet）。 */
    java.util.Map<String, Object> patchDeploymentReplicas(String namespace, String serviceName, int replicas);

    java.util.Map<String, Object> patchStatefulSetReplicas(String namespace, String serviceName, int replicas);

    /**
     * 其它插件工作负载（厂商 `GET /v1/service/otherPlugin`）：跨命名空间列 DaemonSet/Deployment/StatefulSet，
     * 排除 `gaop-namespace`，并套用厂商那串标签选择器。键为 `daemonSetList`/`deploymentList`/`statefulSetList`。
     */
    java.util.Map<String, java.util.List<java.util.Map<String, Object>>> listOtherPluginWorkloads();

    /** 给 Deployment 的所有容器设置 command/args；**Deployment 不存在返回 false**（厂商据此 400）。 */
    boolean patchDeploymentCommand(String namespace, String serviceName, java.util.List<String> command,
                                   java.util.List<String> args);

    /** 取消延时启动（删 `gaop/serviceInitTime` 注解 + `initContainers` 置 null + 整份 replace）；不存在返回 false。 */
    boolean cancelDeploymentStartup(String namespace, String serviceName);

    /** 设置延时启动（0 = 取消；>0 = 写注解 + 注入 initContainer）；Deployment 不存在返回 false。 */
    boolean setDeploymentStartup(String namespace, String serviceName, long delaySeconds,
                                 String harborHost, String initImage);

    // ---------- node_port（厂商 POST /v1/service/{serviceName}/node_port）----------

    /** Deployment 第一个容器的端口声明；**null = Deployment 不存在**，空列表 = 存在但没声明端口。 */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.ContainerPortVO> readDeploymentContainerPorts(
            String namespace, String serviceName);

    boolean serviceExists(String namespace, String serviceName);

    /** 整份 merge-patch Service 的 `spec.ports`/`spec.type`；Service 不存在返回 null。 */
    java.util.Map<String, Object> patchServicePorts(String namespace, String serviceName, String type,
                                                    java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports);

    // ---------- nodePort（厂商 POST /v1/service/{serviceName}/nodePort）----------

    /** 读 Deployment **或** StatefulSet 第一个容器的端口；**两者都不存在返回 null**。 */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.ContainerPortVO> readWorkloadContainerPorts(
            String namespace, String serviceName);

    /** 该命名空间所有 Service 已占用的 nodePort（厂商 `getNodePortUsed`）。 */
    java.util.List<Integer> listUsedNodePorts(String namespace);

    /** 创建 `<name>-nodeport`（骨架照厂商 `generateServiceDoc`）；返回创建后的快照。 */
    java.util.Map<String, Object> createNodePortService(String namespace, String name, String type,
                                                        java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports,
                                                        String workloadName);

    /** patch 已存在的 `<name>-nodeport` 的 ports。 */
    java.util.Map<String, Object> patchNodePortServicePorts(String namespace, String name,
                                                            java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports);

    // ---------- patchService 的 B 分支（厂商 PUT /v1/service/patchService，type != "Service"）----------

    /**
     * 读工作负载的"身份"（Deployment 优先，读不到回退 StatefulSet）：
     * 返回 `metadata.name` 与种类；**两者都不存在返回 null**（厂商据此抛 500）。
     */
    com.csm.paas.core.domain.software.model.valobj.WorkloadRefVO readWorkloadRef(String namespace, String serviceName);

    /**
     * 把工作负载**所有容器**的 `ports` 设成给定列表（厂商 `containers.forEach(c -> c.setPorts(ports))`）
     * 并整份 merge-patch。注意厂商构造容器端口时**只带 `protocol` 与 `containerPort`**（`name` 被丢掉）。
     *
     * @param deployment true 打 Deployment，false 打 StatefulSet
     */
    void patchWorkloadContainerPorts(String namespace, String serviceName, boolean deployment,
                                     java.util.List<com.csm.paas.core.domain.software.model.valobj.ContainerPortVO> ports);

    // ---------- updateVolumes（厂商 POST /v1/service/{service}/updateVolumes）----------

    /**
     * **整份 replace** Deployment 的 `spec.volumes` 与 **第一个容器**的 `volumeMounts`
     * （厂商 `replaceNamespacedDeployment`，`:934`——是 PUT 不是 patch）。
     *
     * <p>厂商语义：`spec.setVolumes(volumes)` 是**替换**不是合并，且只改 `containers.get(0)`。
     *
     * @return 替换后的对象快照；**Deployment 不存在返回 null**
     */
    java.util.Map<String, Object> replaceDeploymentVolumes(
            String namespace, String serviceName,
            java.util.List<com.csm.paas.core.domain.software.model.valobj.WorkloadVolumeVO> volumes,
            java.util.List<com.csm.paas.core.domain.software.model.valobj.VolumeMountVO> volumeMounts);

    // ---------- healthCheck（厂商 PUT /v1/service/healthCheck）----------

    /**
     * 给 Deployment **第一个容器**设置/清空 readiness 与 liveness 探针，然后整份 merge-patch
     * （厂商 `healthCheck:2658-2689` + `patchReadiness/patchLiveness`）。
     *
     * <p>`type`：0 = 清空该探针；1 = HTTPGet（`scheme` 写死 `"HTTP"`）；2 = TCPSocket；其它值不动。
     * 传 null 表示"该探针不动"。
     * <p>容器列表为空时会抛 `IndexOutOfBoundsException`（厂商也没兜，走 500 系统错误）。
     */
    void patchDeploymentProbes(String namespace, String serviceName,
                               com.csm.paas.core.domain.software.model.valobj.HealthCheckVO.HealthSetVO readiness,
                               com.csm.paas.core.domain.software.model.valobj.HealthCheckVO.HealthSetVO liveness);

    // ---------- relationService（厂商 GET /v1/software/relationService/{id}）----------

    /**
     * 读**任意一个 Service** 的原始快照（厂商 `getServiceByName`：按 `metadata.name=` 列，
     * 命中则取第 0 条，出错返回 null）。
     *
     * <p>用途：`mappingInfo.nodePortInfo` 还要合并 `<serviceName>-nodeport` 这个 Service 的端口，
     * 而它是"另一个 Service"，不在工作负载驱动的服务列表里。
     *
     * @return K8s Service 快照；不存在返回 null
     */
    java.util.Map<String, Object> readServiceSnapshot(String namespace, String serviceName);
}
