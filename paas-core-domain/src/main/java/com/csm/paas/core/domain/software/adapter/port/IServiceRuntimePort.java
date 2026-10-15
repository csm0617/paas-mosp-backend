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
     * 与 {@link #queryServices} **同一口径但不分页**。
     *
     * <p>为什么需要：厂商 `tenxCloud.allPageService(null)`（`TenxService.allPageService`）会**翻完所有页**，
     * 而列表端点 `allService(clusterId, page)` 只取首页 10 条——`queryServices` 照抄的是后者。
     * `GET /v1/service/serviceInfoByNode` 用的是前者，所以这里单独开一个"全量"读法。
     */
    List<ServiceRuntimeEntity> queryAllServices(String namespace);

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

    // ---------- rollingUpdate（厂商 PUT /v1/service/rollingUpdate）----------

    /**
     * 滚动更新：按 `type` 重设 `spec.strategy`（1 ⇒ `maxSurge=1/maxUnavailable=0`；2 ⇒ `0/1`；其它不改），
     * 再写注解 `gaop/updateTimestamp`、`minReadySeconds=2`、`containers[0].image=version`，最后整份 merge-patch
     * （厂商 `rollingUpdate:2599-2644` + `setRollingUpdate:2646-2654`）。
     *
     * @return false = Deployment 不存在
     */
    boolean patchDeploymentRollingUpdate(String namespace, String serviceName, Integer type, String version,
                                         long updateTimestamp);

    // ---------- event（厂商 GET /v1/service/event）----------

    /**
     * 按标签选 pod（厂商 `getServiceEvent:2829-2837` 是"列出全部 pod 再在 Java 里按
     * `system/svcName == name` 过滤"，用标签选择器等价且更省）。
     *
     * @return 每个 pod 的 `{name, generateName}`（`generateName` 可能为 null——厂商在那里会 NPE）
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO> listPodsByLabel(
            String namespace, String labelKey, String labelValue);

    /**
     * 列 Event（厂商 `listNamespacedEvent` 的服务端 `fieldSelector`），返回原样快照。
     * <p>两处用法：`involvedObject.name=<rs>,involvedObject.kind=ReplicaSet` 与
     * `involvedObject.name=<pod>,involvedObject.kind=Pod`。
     */
    java.util.List<java.util.Map<String, Object>> listEvents(String namespace, String involvedObjectName,
                                                            String involvedObjectKind);

    // ---------- tolerance（厂商 POST /v1/service/tolerance）----------

    /** 读 Deployment 的 `spec.template.spec.tolerations`；没有（或对象不存在）返回空列表。 */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.TolerationVO> readDeploymentTolerations(
            String namespace, String serviceName);

    /**
     * 整份替换 `spec.template.spec.tolerations` 并 merge-patch（厂商两处都是"设值后整份 patch"）。
     * <p>传空列表 ⇒ 清空（厂商 `type=0` 分支）。
     */
    void patchDeploymentTolerations(String namespace, String serviceName,
                                    java.util.List<com.csm.paas.core.domain.software.model.valobj.TolerationVO> tolerations);

    // ---------- batch-start / batch-stop（厂商 PUT /v1/service/batch-start|batch-stop）----------

    /**
     * 读工作负载的副本与状态读数（Deployment 优先，读不到回退 StatefulSet）；
     * **两者都不存在返回 null**（厂商此时只记日志并跳过该服务）。
     */
    com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO readWorkloadState(String namespace,
                                                                                    String serviceName);

    /**
     * 把工作负载的 `spec.replicas` 设成给定值并**整份 replace**
     * （厂商 `scaleDeployment`/`scaleStatefulSet:3794-3824` 用的是 `replaceNamespaced*`，不是 patch）。
     *
     * @return false = 工作负载不存在
     */
    boolean replaceWorkloadReplicas(String namespace, String serviceName, boolean deployment, int replicas);

    // ---------- batch-restart（厂商 PUT /v1/service/batch-restart）----------

    /**
     * 给工作负载的 pod 模板打上注解 `kubectl.kubernetes.io/restartedAt` 并**整份 replace**
     * （厂商 `serviceRestart:3826-3912` 的滚动重启手法；注解值是 `LocalDateTime.now().toString()`）。
     *
     * @return false = 工作负载不存在
     */
    boolean replaceWorkloadRestartedAt(String namespace, String serviceName, boolean deployment, String restartedAt);

    // ---------- baseComponent（厂商 GET /v1/service/baseComponent）----------

    /** 读某个 DaemonSet 的原样快照；**不存在返回 null**（厂商对读不到只记日志）。 */
    java.util.Map<String, Object> readDaemonSetSnapshot(String namespace, String daemonSetName);

    /**
     * 按"标签 + `spec.nodeName`"列 pod 的原样快照（厂商 `listNamespacedPod(kube-system, fieldSelector=
     * "spec.nodeName=<node>", labelSelector=…)`）。
     */
    java.util.List<java.util.Map<String, Object>> listPodsByLabelAndNode(String namespace, String labelKey,
                                                                        String labelValue, String nodeName);

    /** 列命名空间里**全部** pod 的原样快照（厂商 `overView` 的 pod 统计用 `listNamespacedPod` 无选择器）。 */
    java.util.List<java.util.Map<String, Object>> listAllPods(String namespace);

    /**
     * 读某个 Deployment 的 `spec.template.spec.volumes[*].configMap.name`（厂商
     * `existConfigMapBySoftwareOrService` / `setNameSetBySoftware` 里逐卷收集的那段）。
     *
     * <p>Deployment 不存在或读取失败 ⇒ **空列表**（厂商那两处对 ApiException 都是"记日志后跳过"）。
     */
    java.util.List<String> readDeploymentConfigMapNames(String namespace, String serviceName);

    /**
     * 同上，但 **Deployment 不存在时抛 {@code K8sApiException}**——厂商在
     * `exportConfigMapBySoftwareOrService` 的 service 分支里对 ApiException 是**抛 400**
     * （`service.get.service.config.identifier.exception`，带 key），与 `existConfigmap` 的"跳过"不同。
     */
    java.util.List<String> readDeploymentConfigMapNamesOrThrow(String namespace, String serviceName);

    /**
     * 按名字读 ConfigMap 的原样快照（厂商 `exportConfigMap:3453-3477`）：
     * **读不到的跳过**（只记日志），不抛异常。
     */
    java.util.List<java.util.Map<String, Object>> readConfigMaps(String namespace, java.util.List<String> names);

    // ---------- nodePortUsed（厂商 GET /v1/pub/nodePortUsed）----------

    /** 带**非空** `ingress-lb` 标签的 Deployment 的**标签值**列表（厂商 `getNodePortUsedList:2128-2134`）。 */
    java.util.List<String> listIngressLbValues(String namespace);

    /** 读 Deployment 的 `metadata.annotations`（不存在/无注解 ⇒ 空 Map）。 */
    java.util.Map<String, String> readDeploymentAnnotations(String namespace, String name);

    /** 读 ConfigMap 的 `data`（不存在 ⇒ **null**，厂商按 null 处理）。 */
    java.util.Map<String, String> readConfigMapData(String namespace, String name);

    /** 列命名空间里 `type=NodePort` 的 Service 原样快照。 */
    java.util.List<java.util.Map<String, Object>> listNodePortServices(String namespace);

    /**
     * 列**不带 `ingress-lb` 标签**的 ConfigMap（厂商 `listConfigMap` 传的
     * `labelSelector = "!ingress-lb"`——标签选择器的"该键不存在"语法，**合法**）。
     *
     * <p>返回**整份列表快照**（含 `apiVersion`/`items`/`kind`/`metadata`），因为响应里要带上这些键。
     */
    java.util.Map<String, Object> listConfigMapsWithoutIngressLb(String namespace);

    /**
     * 读一个 ConfigMap；**不存在返回空 Optional**（其余错误抛 `K8sApiException`）。
     * <p>厂商用它区分"404 ⇒ 不存在"与"其它异常 ⇒ 500"。
     */
    java.util.Optional<java.util.Map<String, Object>> findConfigMap(String namespace, String name);

    /** 列命名空间里**全部 Deployment** 的原样快照（厂商 `getV1Deployments`，`relationService` 用）。 */
    java.util.List<java.util.Map<String, Object>> listDeploymentSnapshots(String namespace);

    /** 建一个**只有 metadata.name** 的 ConfigMap（厂商 `createConfigMap:122-152` 的 body 就是空壳）。 */
    java.util.Map<String, Object> createConfigMap(String namespace, String name);

    /**
     * 把某个 ConfigMap 的 `data` **整份替换**成给定内容（厂商 `replaceConfigMap:317-334`：
     * 读到的整份对象改完 data 后 `replaceNamespacedConfigMap`）。
     */
    java.util.Map<String, Object> replaceConfigMapData(String namespace, String name,
                                                       java.util.Map<String, String> data);

    /** 删一个 ConfigMap；**不存在或删除失败返回 false**（厂商对 ApiException 只记日志）。 */
    boolean deleteConfigMap(String namespace, String name);

    /**
     * 列**带 `ingress-lb` 标签**的 ConfigMap（厂商 `exportTcpAndUdpConfigMap:3745` 用的
     * `labelSelector = "ingress-lb"`）。
     */
    java.util.List<java.util.Map<String, Object>> listConfigMapsWithIngressLb(String namespace);

    /** 用整份对象**创建** ConfigMap（厂商 `importConfigMap:3394` 的 `createNamespacedConfigMap`）。 */
    void createConfigMapObject(String namespace, java.util.Map<String, Object> configMap);

    /** 用整份对象**替换** ConfigMap（厂商 `importConfigMap:3383` 的 `replaceNamespacedConfigMap`）。 */
    void replaceConfigMapObject(String namespace, java.util.Map<String, Object> configMap);

    // ---------- 软件整包导出/还原（厂商 SoftwareService.exportSoftwareForms / importSoftwareInfo）----------

    /**
     * type=1（导出全部软件）时那批 Deployment：过滤 + 字段擦拭后的原样快照
     * （厂商 `setSoftwareInfo:847-902`；`gaop/nodeIndex` 在节点信息不可用时恒 `"-1"`）。
     */
    java.util.List<java.util.Map<String, Object>> listSoftwareExportDeployments(String namespace);

    /** type=1 时那批 Service（厂商 `setSoftwareInfo:903-957`）。 */
    java.util.List<java.util.Map<String, Object>> listSoftwareExportServices(String namespace);

    /**
     * type=2（导出指定软件）时按服务名读的 Deployment（厂商 `setSoftwareInfo:958-1020`）：
     * 读不到的**跳过**。
     */
    java.util.List<java.util.Map<String, Object>> readSoftwareExportDeployments(String namespace,
                                                                               java.util.List<String> names);

    /**
     * type=2 时按服务名读的 Service **及其 `&lt;name&gt;-nodeport`**（厂商 `setSoftwareInfo:960-985`）：
     * 读不到的跳过。
     */
    java.util.List<java.util.Map<String, Object>> readSoftwareExportServices(String namespace,
                                                                            java.util.List<String> names);

    /**
     * 还原一个 Deployment：已存在 ⇒ 整份 replace（套 `maxSurge=0/maxUnavailable=1`），
     * 不存在 ⇒ create（厂商 `importSoftwareInfo:1253-1330`）。写失败只记日志、不抛。
     *
     * @param registryHost 当前集群的 registry host（用于替换镜像地址里的旧 host；null 表示沿用原 host）
     */
    void createOrReplaceSoftwareDeployment(String namespace, java.util.Map<String, Object> deployment,
                                           String registryHost);

    /** 还原一个 Service：已存在 ⇒ replace，不存在 ⇒ create（厂商 `createOrUpdateService:354`）。 */
    void createOrReplaceService(String namespace, java.util.Map<String, Object> service);

    // ---------- 服务详情（厂商 GET /v1/service/{serviceName}）----------

    /**
     * 按名取**工作负载驱动的服务项**，**不受列表端点的 10 条分页限制**
     * （厂商详情端点走的是 `tenxCloud.service(clusterID, serviceName)`，不是分页的 `allPageService`）。
     * <p>同时从**全部命名空间**的 pod 里找"第一个容器名 == 服务名"的那个，填 `hostIp` /
     * `terminatedSnapshot`（厂商 `getServiceDetail:1416-1431`）。
     *
     * @return 工作负载（Deployment 优先，再 StatefulSet）不存在时返回 **null**（厂商据此抛 400）
     */
    ServiceRuntimeEntity lookupServiceDetail(String namespace, String serviceName);

    // ---------- updateServiceRole（厂商 PUT /v1/service/updateServiceRole）----------

    /**
     * 给 Deployment 的 pod 模板 metadata 做整份 merge-patch（厂商 `updateServiceRole:1513-1518`
     * 的 `json.serialize(v1Deployment)` + `application/merge-patch+json`）。
     *
     * @return false = Deployment 不存在（厂商此时 patch 抛异常 ⇒ 走 StatefulSet 回退）
     */
    boolean patchDeploymentTemplateMetadata(String namespace, String name,
                                            java.util.Map<String, String> annotations);

    /**
     * 读工作负载（Deployment 优先，读不到回退 StatefulSet）pod 模板的
     * `spec.template.metadata.annotations`；**两者都不存在、或该字段为 null ⇒ null**
     * （厂商 `updateServiceRole:1507-1511` 直接把这个可能为 null 的 Map 当结果用）。
     */
    java.util.Map<String, String> readWorkloadTemplateAnnotations(String namespace, String name);

    /** 同上，打在 StatefulSet 上（厂商回退路径）。 */
    boolean patchStatefulSetTemplateMetadata(String namespace, String name,
                                             java.util.Map<String, String> annotations);

    // ---------- schedulingNode / reStartBaseService（SSH，类 C）----------

    /**
     * 列出全部节点，并算出"master 节点 IP"（厂商 `schedulingNode:3096-3112` /
     * `reStartBaseService:3321-3337`）。节点标签键是 `node-role.kubernetes.io/<nodeRole>`。
     * <p>厂商遍历**所有**节点、只要含该标签就覆盖 `ip`（最后一个命中者生效）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.NodeInfoVO> listNodes();

    // ---------- servicePods（厂商 DELETE /v1/service/servicePods）----------

    /**
     * 删一个 pod（厂商 `deleteServicePods:2330-2343`）。
     * <p>`force` 为 true ⇒ `gracePeriodSeconds = 0`；否则不带该字段（API Server 默认宽限期）。
     * <p>**破坏性操作**，照抄厂商不做额外校验。失败抛 `K8sApiException`。
     */
    void deletePod(String namespace, String podName, Boolean force);

    // ---------- resetService（厂商 POST /v1/software/reset_service）----------

    /**
     * 命名空间里全部 Deployment 的 `名字 → metadata.labels`（`labels` 可能为 null），
     * 用于判断有没有 `ingress-lb` 标签。读失败抛 `K8sApiException`。
     */
    java.util.Map<String, java.util.Map<String, String>> listDeploymentLabels(String namespace);

    /** 删一个 Deployment（厂商 `deleteNamespacedDeployment`）；失败抛 `K8sApiException`。 */
    void deleteDeployment(String namespace, String name);

    // ---------- top（厂商 POST /v1/software/top 的 getCpuNum）----------

    /** 读某 pod 的 `status.hostIP`；pod 不存在 ⇒ **null**。失败抛 `K8sApiException`。 */
    String readPodHostIp(String namespace, String podName);

    /**
     * 在节点列表里找 `status.addresses` 里 **InternalIP == 给定值**的节点，
     * 返回其 `status.allocatable["cpu"]`（厂商 `getCpuNum:1886-1908` 的 `quantity.getNumber()`）。
     * <p>找不到 / 没有该量 ⇒ **null**（厂商保持默认 1 核）。
     */
    java.math.BigDecimal readNodeAllocatableCpuByInternalIp(String internalIp);

    // ---------- 作战模式切换（厂商 ModeServiceImpl.getListWithOutGaop / updateServiceEnv）----------

    /**
     * 列"业务软件"Deployment 的名字与**第一个容器**的 env
     * （厂商 `ModeServiceImpl.getListWithOutGaop:229-248` 的过滤规则 +
     * `startModeType:119-120` 取 env）。
     *
     * <p>厂商数据源是 `TenxCloud.allPageService(null)`（腾讯云 API，**类 C 不可验证**）；
     * 本平台没有该客户端，改用**同集群的 Deployment 直读**等价复刻——过滤规则逐条照抄：
     * <ol>
     *   <li>`metadata.labels == null` ⇒ 丢弃；</li>
     *   <li>`labels["system/appType"] != null` ⇒ 只保留值等于 `business` 的；</li>
     *   <li>否则看 `labels["system/appName"]`：`gis-server` / `gis-jb` ⇒ 丢弃，
     *       `gaop` ⇒ 丢弃，其余保留。</li>
     * </ol>
     * <p>`env` 为 **null** 表示容器没有 env 字段；容器列表为空时抛 `IndexOutOfBoundsException`
     * （厂商 `containers.get(0)` 同样会抛 ⇒ 500，照抄）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.DeploymentEnvVO>
    listBusinessServiceDeploymentEnvs(String namespace);

    /**
     * 读 Deployment **第一个容器**的 env（厂商 `ModeServiceImpl.updateServiceEnv:162`
     * 的 `readNamespacedDeployment`）。
     *
     * @return **null = Deployment 读不到**（厂商此时抛 400 `service.get.service.info.not.found`）；
     *         否则返回 VO，其 `env` 可能为 **null**（容器没有 env 字段）
     */
    com.csm.paas.core.domain.software.model.valobj.DeploymentEnvVO readDeploymentEnv(
            String namespace, String serviceName);

    /**
     * 把 Deployment **第一个容器**的 env **整份替换**成给定列表并 merge-patch
     * （厂商 `ModeServiceImpl.updateServiceEnv:176-205` 的最后一步：改完容器后整份 patch）。
     *
     * <p>合并逻辑（"同名 setValue + 剩余的追加"）在领域层做（见 {@code ModeCommandServiceImpl}），
     * 因为它与 K8s 无关、且厂商那段的 catch/return false 语义要在域层保留。
     *
     * @return false = Deployment 读不到（厂商此时抛 400 `service.get.service.info.not.found`）
     * @throws com.csm.paas.core.types.exception.K8sApiException patch 失败
     */
    boolean replaceDeploymentEnv(String namespace, String serviceName,
                                 java.util.List<com.csm.paas.core.domain.software.model.valobj.EnvVarVO> env);

    // ---------- 最后一批端点（nodeMonitor / serviceMonitor / nodeUsage / node / patch / serviceInfoByNode）----------

    /**
     * 删一个**节点**（厂商 `TenxCloudImpl.deleteNode:785-800` 的
     * `coreV1Api.deleteNode(nodeName, null, null, null, null, null, null)`）。
     *
     * <p><b>破坏性</b>：直接把节点对象从集群里删掉（kubelet 仍在跑的节点会被驱逐工作负载）。
     * 照抄厂商：**不做任何前置校验**（节点不存在时由 K8s 返回 404，调用方翻成 400
     * `alert.delete.node.info.error`）。
     *
     * <p>失败抛 {@code K8sApiException}。
     */
    void deleteNode(String nodeName);

    /**
     * 读 Deployment 的 `metadata.labels`（厂商 `ComputerService.getCpuNumByService:1015-1023` /
     * `getOutBoundNetWorkMonitor:1188-1196` / `getInBoundNetWorkMonitor:1230-1238` 都只取
     * **第一个 entry** 拼成 `key=value` 当标签选择器）。
     *
     * @return Deployment 不存在 ⇒ **null**（厂商此时 `readNamespacedDeployment` 抛 ApiException、
     *         被 catch 成"选择器为空串"）
     */
    java.util.Map<String, String> readDeploymentLabels(String namespace, String serviceName);

    /**
     * 用整份 **apps/v1 Deployment** 快照创建 Deployment（厂商 `TenxService.createDeploymentYaml:1134-1150`
     * 与 `createAppDeployment:2574-2600` 的 `createNamespacedDeployment`）。
     *
     * <p>用途：`PUT /v1/service/patch` 开头那段"时速云 App"逻辑在本平台是**原生 K8s**实现
     * （`existApp`/`existService` 都是按标签列 Deployment/StatefulSet），其净效果就是
     * "Deployment 不存在就按导入的 YAML 建一个"。见 {@code ServiceQueryServiceImpl.updateServiceConfig}。
     *
     * @param deployment 已规范化（apps/v1、镜像 host 替换、metadata 清理）的整份快照
     */
    void createDeploymentSnapshot(String namespace, java.util.Map<String, Object> deployment);

    /**
     * 用整份 Deployment 快照 **replace**（厂商 `ServiceController.updateServiceConfig:1259`
     * 的 `replaceNamespacedDeployment`——PUT 不是 patch）。
     */
    void replaceDeploymentSnapshot(String namespace, java.util.Map<String, Object> deployment);

    /**
     * 用整份 Service 快照创建 Service（厂商 `TenxService.createServiceYaml:1152-1178`，
     * `createApp` 里用来把 YAML 的第二份文档建成 Service）。
     */
    void createServiceSnapshot(String namespace, java.util.Map<String, Object> service);

    /**
     * 删一个 Service（厂商 `ServiceController.updateServiceConfig:1363` 的 `deleteNamespacedService`，
     * 请求里没有 `nodePortService` 而集群里存在 `<name>-nodeport` 时走这条）。
     *
     * <p>失败抛 {@code K8sApiException}；Service 不存在时厂商只记日志（不报错），调用方照抄这个语义。
     */
    void deleteService(String namespace, String serviceName);

    /**
     * 节点名 → `status.capacity.memory` 的 **KiB** 值（厂商 `TenxService.getNodes:1913-1919` 的
     * `memory_total_kb = capacity["memory"] / 1024`）。
     *
     * <p>用途：`GET /v1/pub/nodeUsage` 把 Prometheus 的"已用字节数"换算成百分比
     * （`data / 1024 / memory_total_kb * 100`）。
     */
    java.util.Map<String, Long> listNodeCapacityMemoryKb();
}
