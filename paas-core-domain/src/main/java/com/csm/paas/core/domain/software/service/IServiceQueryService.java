package com.csm.paas.core.domain.software.service;

import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;

/** 软件-服务-端口 域的查询服务。 */
public interface IServiceQueryService {

    ServiceListEntity queryServiceList(String namespace);

    /**
     * 节点 label 的键与去重值（厂商 `GET /v1/service/labels`）。
     * <p>返回 `label键 → 该键的去重值列表`；**顺序不可依赖**（厂商用 HashMap 聚合）。
     */
    java.util.Map<String, java.util.List<String>> queryNodeLabels();

    /**
     * 改服务显示名（厂商 `PUT /v1/service/updateName`，`ServiceController:1536-1561`）：
     * <ol>
     *   <li>先给 **Deployment** 打 patch，把注解 `gaop/serviceLabel` 换成 `name`；</li>
     *   <li>上面**任意异常**都回退到给 **StatefulSet** 打同样的 patch；</li>
     *   <li>回退也抛 `ApiException` → `service.modify.name.failed`（HTTP 500，走 `-1` 信封）；</li>
     *   <li>成功（含回退成功）都落 i18n 自动名 `i18n.service.{en_US,fr_FR}.{serviceKey}` 并返回 true。</li>
     * </ol>
     * <p><b>未端到端验证</b>：这条会真的改 K8s 工作负载；拿厂商环境的工作负载做实验违反本项目的清理纪律，
     * 所以只用**离线单测**钉住"patch 报文内容 + 回退顺序 + i18n 落库"，线上只对拉了"服务不存在时的错误契约"。
     */
    boolean updateServiceName(String namespace, String serviceKey, String name, String nameUs, String nameFr);

    /**
     * 框架/基础服务列表（厂商 `GET /v1/service/gaop?page&size&type&keyword`，`ServiceController:1563-1660`）。
     * <p>过滤与 `fixedName` 见 `GaopServiceFilter` / `GaopServiceNames`（含照抄的厂商缺陷）；
     * `keyword` 两段式匹配；分页用厂商同款 `PageUtils` 口径。
     * <p>`keyword` 长度 &gt; 45 → `service.invalid.service.name.length`（HTTP 400），由调用方（控制器）先判。
     */
    ServiceListEntity queryGaopServiceList(String namespace, int page, int size, Integer type, String keyword);

    /**
     * 授权视图的服务列表（厂商 `GET /v1/service/serviceAuth`，`ServiceController:3478-3560`）。
     *
     * <p>三段过滤（详见 `docs/findings/59`）：
     * <ol>
     *   <li>保留 `system/appName` **既不是 `gaop` 也不是 `gis-server`** 的服务
     *       —— 注意与 `queryGaopServiceList` 的 `appName == "gaop"` **正好互补**；</li>
     *   <li>keyword 两段式（与 gaop 同款：名字 contains → 否则 `gaop/serviceLabel` contains）；</li>
     *   <li>`authType` 非空时按授权状态过滤（有授权行按 `auth` 命中；无授权行按三条分支）。</li>
     * </ol>
     *
     * @param authType   逗号分隔的授权状态列表（0未授权 1已授权 2已过期 3授权中 4授权失败）；空=不过滤
     * @param serviceType 厂商签名里的 `serviceType` 参数，**在过滤链里未被使用**（照抄：它只出现在入参上）
     */
    ServiceListEntity queryServiceAuthList(String namespace, int page, int size, String keyword,
                                          String authType, String serviceType);

    /**
     * 改服务 hostname（厂商 `PUT /v1/service/hostName`，`ServiceController:2693-2712`）。
     *
     * <p>逐行照抄的三点：
     * <ol>
     *   <li>先读 **Deployment**；读不到（ApiException）**回退 StatefulSet**（同一套逻辑）；</li>
     *   <li>**若请求的 hostName 与现有 hostname 都是空白 → 直接返回 true，什么也不改**（静默 no-op）；</li>
     *   <li>真正修改时是"读→改→整份 `application/merge-patch+json`"。</li>
     * </ol>
     */
    boolean updateServiceHostname(String namespace, String serviceName, String hostName);

    /**
     * 改服务优先级（厂商 `POST /v1/service/priority?serviceName&level`，`ServiceController:3029-3050`）。
     * <p>逐行照抄：**只读 Deployment、没有 StatefulSet 回退**；读不到 → 400 `service.change.priority.failed`；
     * `level` 空白时把 `priorityClassName` 设成**空串**（不是 null）；patch 失败 → `service.update.service.config.error`。
     */
    boolean updateServicePriority(String namespace, String serviceName, String level);

    /**
     * 改副本数（厂商 `PUT /v1/service/scale?serviceName&scale`，`ServiceController:3063-3085`）。
     *
     * <p>逐行照抄：用 **strategic-merge-patch** `{"spec":{"replicas":N}}` 打 Deployment，
     * 成功则返回**整份 patch 后的对象**；**任意异常**都回退 StatefulSet；两者都失败 →
     * `SystemException(异常消息)`（HTTP 500 / `code=-1`）。
     */
    java.util.Map<String, Object> scaleService(String namespace, String serviceName, int replicas);

    /**
     * 其它插件工作负载（厂商 `GET /v1/service/otherPlugin`，`ServiceController:4058-4090`）：
     * 三类工作负载**跨命名空间**、排除 `gaop-namespace`、套厂商的标签选择器；
     * 某一类取失败时厂商只记日志并返回**空列表**（不是整体报错）。
     */
    java.util.Map<String, java.util.List<java.util.Map<String, Object>>> queryOtherPluginWorkloads();

    /**
     * 改服务启动命令（厂商 `PUT /v1/service/patchCommand/{serviceName}`，`ServiceController:992-1010`）。
     * <p>逐行照抄：读不到 Deployment → 400 `service.service.name.not.found`（**无 StatefulSet 回退**）；
     * 对**所有容器**设置 `command`/`args`；patch 失败 → `service.modify.start.command`（500）。
     * <p>厂商返回 `Response.ok()`（**不带 body**）⇒ 响应的 `data` 是 **null**。
     */
    boolean updateServiceCommand(String namespace, String serviceName,
                                 java.util.List<String> command, java.util.List<String> args);

    /**
     * 某服务的 Pod 重启记录（厂商 `GET /v1/service/restartInfo?serviceName=`，
     * `ServiceController:3950-3965`）：查 `g_pod_event`（`service_name` 等值、`create_at` 倒序），
     * 映射成 `{podName, createTime, restartCount}`；**没有记录就返回空数组**（不报错）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.PodRestartInfoVO> queryRestartInfo(
            String serviceName);

    /**
     * 取消延时启动（厂商 `POST /v1/service/cancelStartUp` → `ServiceController:4030-4055`）。
     *
     * <p>逐个服务：读 Deployment（**读不到 → 400 `service.get.service.config.exception.cancel.delay`**，参数是服务名）
     * → 删注解 `gaop/serviceInitTime` → `initContainers` 置 null → **整份 `replace`**。
     * <p>全部成功返回 true；**列表里有一个失败就中断**（厂商在循环里直接抛）。
     */
    boolean cancelServiceStartup(String namespace, java.util.List<String> serviceNames);

    /**
     * 设置延时启动（厂商 `POST /v1/service/startUp` → `ServiceController:3968-4026`）。
     *
     * <p>`time < 0` → 400 `service.negative.startup.delay`；
     * 逐个服务：读 Deployment（读不到 → 400 `service.get.service.config.exception.set.delay`，参数为服务名）
     * → `time == 0` 走"取消"（删注解 + 清 initContainers + 整份 replace）；
     * `time > 0` 走"设置"（写注解 `gaop/serviceInitTime` + 注入 initContainer，镜像为
     * `<harborIp>/system_containers/<initPodConfig.image>`）。
     */
    boolean setServiceStartup(String namespace, java.util.List<String> serviceNames, long time,
                              String harborHost, String initImage);

    /**
     * 设置服务的 NodePort（厂商 `POST /v1/service/{serviceName}/node_port` → `patchServicePorts:701-815`）。
     *
     * <p>两个分支（详见 `docs/findings/85`）：
     * <ul>
     *   <li>**A**：`type == "NodePort"` 且 `nodePorts` 为空 → 读 Deployment 的第一个容器端口**推导** Service 端口；
     *       Deployment 不存在 → 500（消息里带服务名）；Service 不存在 → 500「设置NodePort失败：service为空」；
     *       **容器没有声明端口时什么都不做，返回 false**（⇒ 响应 `data:false`，静默失败）；</li>
     *   <li>**B**：其它 → 按 `nodePorts` 列表构建（`type == "NodePort"` 时才填 `nodePort`）。</li>
     * </ul>
     * 两分支最后都整份 merge-patch Service；端口被占用（K8s 422 + `provided port is already allocated`）→ 400
     * `service.port.occupied`，其它 API 异常 → 500 `service.update.nodeport.failed`。
     *
     * @return 是否真的改动了 Service（对应厂商 `Response.ok(v1Service != null)`）
     */
    boolean updateServiceNodePort(String namespace, String serviceName, String type,
                                  java.util.List<com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO> nodePorts);

    /**
     * 设置 NodePort（厂商 `POST /v1/service/{serviceName}/nodePort` → `updateServicePort:482-670`，详见 `findings/88`）。
     *
     * <p>与 `updateServiceNodePort`（`/node_port`）的区别：**本端点还管理另一个 `<serviceName>-nodeport` Service**
     * （不存在则按 `generateServiceDoc` 骨架创建，存在则 patch）。
     * <p>四处照抄的怪点：主 Service 的 type **写死 `ClusterIP`**、`<name>-nodeport` 用请求的 type；
     * `nodePort` 为空时**随机**取 [10000, 75534]（≤10 次重试避开已用端口）；请求 type 不是 `ClusterIP` 时
     * **返回 false**；主 Service 的端口里 `nodePort` 显式置 null。
     *
     * @return 是否真的创建/更新了 `<name>-nodeport`
     */
    boolean updateServiceNodePortsEndpoint(String namespace, String serviceName, String type,
                                           java.util.List<com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO> nodePorts);

    /**
     * 修改服务发现的配置信息（厂商 `PUT /v1/service/patchService` → `ServiceController.updateServiceDiscovery:1014-1163`，
     * 规格见 `docs/findings/118`）。
     *
     * <p>按 `type` 分两条路径：
     * <ul>
     *   <li><b>`type == "Service"`</b>：读 Service 与工作负载的第一个容器端口 →
     *       用容器端口重建 `spec.ports`（`name` = `"<protocol>-<serviceName>-<i>"` 小写、`appProtocol` = protocol、
     *       `nodePort` = null、`targetPort` = containerPort）→ `spec.type` **写死 `ClusterIP`** → 整份 merge-patch；</li>
     *   <li><b>其它 `type`</b>：按 `g_software_app.app_name`（用工作负载的 **`metadata.name`**）找软件 →
     *       删光并重写 `g_service_port`（`path_name = "<name>-<port>"`、`allow=true`、`web_entry=false`）→
     *       通知网关刷新 → 把**所有容器**的 `ports` 换成入参的 `containerPort`/`protocol`（**`name` 被丢掉**）。</li>
     * </ul>
     *
     * <p><b>错误语义（两条路径不同，都是照抄厂商）</b>：
     * <ul>
     *   <li>A：Service 不存在 → 400 `k8s.call.api.failed`（厂商 `readNamespacedService` 对 404 **抛异常**而不是返回 null，
     *       被外层 `catch (ApiException)` 接住；源码里那句「设置NodePort失败：service为空」是死代码）；
     *       工作负载不存在、以及其它任何异常 → 400 `service.update.service.discovery.failed`
     *       （厂商内层抛的 `SystemException` 被外层 `catch (Exception)` 吞掉）；</li>
     *   <li>B：工作负载不存在 → **500**「修改dployment/statefulSet失败，找不到该服务」（这个 `SystemException`
     *       抛在 try **外面**，所以真的发得出去）；K8s API 异常 → 400 `service.update.service.config.error`；
     *       其它（含 `portList` 为 null/空）→ 400 `service.update.service.details.failed`。</li>
     * </ul>
     *
     * <p><b>一处主动收紧</b>（`docs/deviations.md` B14）：B 分支 `portList` 为空时**先校验再删**——
     * 厂商是先删 `g_service_port` 再让 `insertBatch` 拼出坏 SQL 报错，会白丢端口映射。
     *
     * @return 恒为 `true`（厂商 `Response.ok(true)`；成功路径）
     */
    boolean updateServiceDiscovery(String namespace,
                                   com.csm.paas.core.domain.software.model.valobj.UpdateServiceDiscoveryVO form);

    /**
     * 创建/编辑服务的文件映射与文件挂载（厂商 `POST /v1/service/{service}/updateVolumes` →
     * `ServiceController.updateServiceVolumes:818-947`，规格见 `docs/findings/119`）。
     *
     * <p>**两处"替换式"语义**（照抄）：`spec.volumes` 是**整份替换**（不是合并，已有卷会被清掉）；
     * 挂载只写给**第一个容器**，其它容器不动。
     *
     * <p>四处怪点：configMap 卷的 `items[].path` 用的是 **key**（不是 `dir`），而挂载路径是 `dir + "/" + key`；
     * hostPath 的 `type` 是**空串**；`readOnly == 2` 才置 true（否则该字段根本不写）；
     * 卷名带 **5 位随机字符**（`tenxcloud-time-localtime` 除外）。
     *
     * <p>错误语义：Deployment 不存在 → 400 `service.service.name.not.found`（带服务名）；
     * 组装阶段任何异常（含"容器列表为空"）→ 500 `service.file.mapping.or.mount.failed`；
     * replace 的 K8s 异常 → 500 `service.update.service.config.failed`。
     *
     * @return replace 之后的 Deployment 快照（厂商返回整份对象）
     */
    java.util.Map<String, Object> updateServiceVolumes(
            String namespace, String serviceName,
            com.csm.paas.core.domain.software.model.valobj.UpdateServiceVolumesVO form);

    /**
     * 设置/清空服务（Deployment 第一个容器）的健康检查探针
     * （厂商 `PUT /v1/service/healthCheck` → `ServiceController.healthCheck:2658-2689`，规格见 `docs/findings/121`）。
     *
     * <p>`type`：0 = 清空；1 = HTTPGet（`scheme` 写死 `"HTTP"`）；2 = TCPSocket；其它值（含 null，我们收紧）不动。
     * `readiness`/`liveness` 为 null 表示"该探针不动"。
     *
     * <p>错误语义：Deployment 不存在 → 400 `service.not.found.update.image.version`（带服务名）；
     * patch 的 K8s 异常 → 400 `k8s.call.api.failed.to.change.image.version`；
     * **容器列表为空 ⇒ 500 `系统错误`/`code=-1`**（厂商没兜这段，我们也不兜）。
     *
     * @return 恒为 `true`（厂商 `Response.ok(true)`）
     */
    boolean updateHealthCheck(String namespace,
                              com.csm.paas.core.domain.software.model.valobj.HealthCheckVO form);

    /**
     * 某软件**关联的服务**列表（厂商 `GET /v1/software/relationService/{id}` →
     * `SoftwareService.softwareRelationServiceList:3064-3158`，规格见 `docs/findings/120`）。
     *
     * <p>与 {@link #queryServiceList} 的区别（四处）：
     * <ol>
     *   <li>按该软件的 `g_software_app.app_name` **过滤**（只留 `deployment.metadata.name` 命中的）；</li>
     *   <li>`mappingInfo` **真的填**：`intranetInfo`/`nodePortInfo` 由 Service 与 `<name>-nodeport` 的端口算出，
     *       `ingressInfo` **恒空**（厂商读的 `IoK8sApiCoreV1Service.proxy` 全仓库无赋值 ⇒ 恒 null）；</li>
     *   <li>授权：没有 `g_service_auth` 行且 `gaop/serviceType == "前台服务"` ⇒ 造 `{auth:1, authType:1}`；
     *       有行且 `now > authTime` ⇒ 改成 `EXPIRED(2)` 并**写库**（厂商的读接口带副作用）；</li>
     *   <li>把 `deployment` 快照里注解 `gaop/serviceLabel` 换成当前语言的自动名
     *       （厂商 `getAutoNameMessageByCurrent("service", key, name)`：**只有 en_US/fr_FR 会查覆盖表**，
     *       其它语言直接返回原值）。</li>
     * </ol>
     *
     * <p><b>一处主动收紧</b>（`deviations.md` B17）：厂商对"`app_name` 在 k8s 里找不到对应服务"**不判空**
     * ⇒ 立刻 NPE ⇒ 500。我们**跳过该行**（关联残留是常见状态，照抄等于让整页 500）。
     *
     * <p>软件不存在 ⇒ 400 `software.get.software.service.info.failed`。
     */
    java.util.List<com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity>
    querySoftwareRelationServices(String namespace, String softwareId);

    /**
     * 滚动更新 / 更新镜像版本（厂商 `PUT /v1/service/rollingUpdate` →
     * `ServiceController.rollingUpdate:2599-2644`，规格见 `docs/findings/122`）。
     *
     * <p>`type`：1 ⇒ `maxSurge=1/maxUnavailable=0`；2 ⇒ `0/1`；**其它值不改策略但照样改镜像**。
     * 另外一律写注解 `gaop/updateTimestamp=<now>`、`minReadySeconds=2`、`containers[0].image=version`。
     *
     * <p>patch 成功后还要把关联软件的 `g_software.updated_at` 推进到当前时间
     * （厂商 `updateSoftwareTimeByService:3776-3788`：按 `g_software_app.app_name` 找软件逐个更新）。
     *
     * <p>错误语义：Deployment 不存在 → 400 `service.not.found.update.image.version`（带服务名）；
     * patch 的 K8s 异常 → 400 `k8s.call.api.failed.to.change.image.version`（**失败时不动库**，照抄顺序）。
     *
     * @return 恒为 `true`（厂商 `Response.ok(true)`）
     */
    boolean updateServiceImageRollingUpdate(String namespace,
                                            com.csm.paas.core.domain.software.model.valobj.ServiceImageVO form);

    /**
     * 某服务的 Event 事件列表（厂商 `GET /v1/service/event?name=` →
     * `ServiceController.getServiceEvent:2821-2859`，规格见 `docs/findings/123`）。
     *
     * <p>流程：按标签 `system/svcName=<name>` 找 pod → 取第一个 pod 的 `generateName` **去掉末位字符**
     * 作为 ReplicaSet 名 → 查 `involvedObject.name=<rs>,involvedObject.kind=ReplicaSet` 的事件 →
     * 再对每个 pod 查 `involvedObject.name=<pod>,involvedObject.kind=Pod` 的事件 →
     * 按 `lastTimestamp` **降序**（且**空值排最前**，照抄厂商 `nullsLast(...).reversed()` 的实际效果）。
     *
     * <p>没有匹配的 pod ⇒ 直接返回空列表（200）。
     * <p>K8s 调用异常 ⇒ 400 `service.get.service.event.info.exception`。
     * <p><b>一处主动收紧</b>（`deviations.md` B18）：pod 的 `generateName` 为 null 时**跳过该 pod**
     * （厂商直接 `substring` ⇒ NPE ⇒ 500）。
     *
     * @return 原样 Event 快照列表（字段名即 K8s 事件 schema）
     */
    java.util.List<java.util.Map<String, Object>> queryServiceEvents(String namespace, String serviceName);

    /**
     * 开启/关闭 master 节点污点容忍（厂商 `POST /v1/service/tolerance?serviceName&type=` →
     * `ServiceController.allowMasterTolerance:3425-3475`，规格见 `docs/findings/124`）。
     *
     * <p>`type == 0`（关闭）：**tolerations 本来就为空时直接返回 true 且不打 patch**；否则整份置空并 patch。
     * <br>`type == 1`（开启）：整份置成**那一条**固定容忍
     * `{key: "node-role.kubernetes.io/master", operator: "Exists", effect: "NoSchedule"}` 并 patch。
     * <br>**其它 type** ⇒ 返回 `false`（HTTP 200 但 `data:false`，不打 patch）。
     *
     * <p>错误语义：Deployment 读不到 → 400，键是厂商源码里那个**拼重了的字面量**
     * `service.not.found.update.taintservice.not.found.update.taint`（消息包解析失败时回退成键本身，
     * 与厂商的 `i18n()` 一致 ⇒ **响应文案也一致**）；patch 失败 → 400 `service.update.service.failed`（带服务名）。
     *
     * <p><b>未照抄</b>：厂商方法开头的 `Thread.sleep(100)`（对响应无可观测影响，见 findings）。
     */
    boolean allowMasterTolerance(String namespace, String serviceName, Integer type);

    /**
     * 基础服务（kube-system 里的组件）的 Event 列表（厂商 `GET /v1/service/baseEvent?baseServiceName=` →
     * `ServiceController.getBaseServiceEvent:3377-3421`，规格见 `docs/findings/125`）。
     *
     * <p>与 {@link #queryServiceEvents} 的四处不同：
     * <ol>
     *   <li>命名空间**写死 `kube-system`**；</li>
     *   <li>标签选择器：`kube-proxy` ⇒ `k8s-app=<name>`，**其它** ⇒ `name=<name>`；</li>
     *   <li>ReplicaSet 名**不是**从 pod 的 `generateName` 推的，而是 `<baseServiceName>-`（直接用）；</li>
     *   <li>排序是"按 `lastTimestamp` 降序"（**没有 `nullsLast` 保护**——见下）。</li>
     * </ol>
     *
     * <p>三个错误键分工：列 pod 失败 ⇒ 400 `service.get.basic.service.container.info.failed`；
     * ReplicaSet 事件失败 ⇒ 400 `service.get.container.event.info.failed`；
     * 每个 pod 的事件失败 ⇒ 400 `service.get.container.pod.event.info.failed`。
     *
     * <p><b>一处主动收紧</b>（`deviations.md` B19）：厂商的 `Comparator.comparing(getLastTimestamp).reversed()`
     * **没有空值保护** ⇒ 事件缺 `lastTimestamp` 时比较器抛 NPE ⇒ 500。我们把空值**排到最后**。
     */
    java.util.List<java.util.Map<String, Object>> queryBaseServiceEvents(String baseServiceName);

    /**
     * 批量**停止**服务（厂商 `PUT /v1/service/batch-stop`，body 是服务名数组 →
     * `ServiceController.serviceStop:3649-3685`，规格见 `docs/findings/126`）。
     *
     * <p>逐个服务：读 Deployment（读不到回退 StatefulSet；都没有则**跳过**）→
     * `spec.replicas == 0` 也跳过 → 否则整份 replace 把副本数设成 **0**。
     * replace 失败 ⇒ 400 `service.operation.failed`（`{0}` 是 `"停止"`）。
     *
     * <p>入参为空 ⇒ 直接返回 true（不碰集群）。
     *
     * @return 恒为 `true`（厂商 `Response.ok(true)`）
     */
    boolean batchStopServices(String namespace, java.util.List<String> serviceNames);

    /**
     * 批量**启动**服务（厂商 `PUT /v1/service/batch-start` → `serviceStart:3689-3740`）。
     *
     * <p>与 {@link #batchStopServices} 不同：它**不看 `spec.replicas`**，而是按 `status` 推一个状态串
     * （`getServiceStatusByServicesItem` 的两个重载，Deployment 与 StatefulSet 的判断条件**不同**）：
     * 只有 `"STOP"` 才把副本数设成 1；`"RUNNING"` / `" ROLLING_UPDATE"`（**注意前导空格**）/ `"STARING"`
     * （厂商的拼写）都只记日志不动作。
     *
     * <p>入参为空 ⇒ 直接返回 true。
     */
    boolean batchStartServices(String namespace, java.util.List<String> serviceNames);
}
