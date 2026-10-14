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
     * 服务详情（厂商 `GET /v1/service/{serviceName}` → `ServiceController.getServiceDetail:1404-1481`）。
     *
     * <p>逐行照抄的点：
     * <ol>
     *   <li>服务不存在 ⇒ 400 **`service.service.name.not.found`**（带服务名）；</li>
     *   <li>遍历**全部命名空间**的 pod，找"**第一个容器名 == 服务名**"的那个：取它的 `status.hostIP`；
     *       若它的 `status.containerStatuses[0].lastState.terminated != null` 就记下 `terminated` 并 **break**
     *       （注意厂商一旦进到那个 pod 就会 break，**不管** terminated 是否为空）；</li>
     *   <li>`imageInfo` = Deployment 的 `spec.template.spec.containers[0].image`；</li>
     *   <li>`<serviceName>-nodeport` 的 Service：读得到就**裁剪**成 `{ports:[…], type}`（`targetPort` 只取
     *       `IntOrString` 的 int 值 ⇒ **具名端口是 null**），读不到 ⇒ `nodePortService = null`；</li>
     *   <li>`nameUs`/`nameFr` = `i18n.service.<en_US|fr_FR>.<key>`，**空白则回退**注解开
     *       `gaop/serviceLabel`；key 是 Deployment 的 `metadata.name`。</li>
     * </ol>
     *
     * <p><b>一处主动收紧</b>（deviations B24）：厂商在 pod 的 `containers` 为空时 `name` 保持 null，
     * 紧接着 `name.equals(serviceName)` ⇒ **NPE ⇒ 500**。我们**跳过**这类 pod。
     * <p><b>未填充</b>：`softwareNameList` / `podInfoList` / `belongNodeList` / `cpuOrder` / `memoryOrder` /
     * `gpuOrder`——前两个依赖监控域（Prometheus），后三个依赖 `computerService` 的 24 小时指标；
     * 厂商详情端点里 `restartCount` 恒为 `0`（字段初始化值，从不赋值），我们照抄 `0`。
     *
     * @return 含 `terminatedSnapshot` / `hostIp` / `nodePortServiceSnapshot` / `nameUs` / `nameFr` 的实体
     * @throws com.csm.paas.core.types.exception.WrongArgumentException `service.service.name.not.found`
     */
    com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity queryServiceDetail(
            String namespace, String serviceName);

    /**
     * 更新服务的战位标识（厂商 `PUT /v1/service/updateServiceRole?serviceKey&name` →
     * `ServiceController.updateServiceRole:1483-1531`）。
     *
     * <p>先读 Deployment `name`；读不到回退 StatefulSet 并把 `isDeployment` 置 false；
     * **两者都读不到 ⇒ 400 `service.get.service.failed.not.found`**。
     *
     * <p><b>照抄的厂商缺陷</b>：厂商先 `new HashMap<>()` 并 `put("gaop/serviceRole", serviceKey)`，
     * 然后**立刻把变量重新赋值**成工作负载自己的 `spec.template.metadata.annotations`：
     * <ul>
     *   <li>工作负载的 annotations **为 null** 时：`setAnnotations(那个 HashMap)` ⇒
     *       patch 后注解**只有** `gaop/serviceRole`；</li>
     *   <li>annotations **非 null** 时：那个 put 被丢弃 ⇒ patch 出去的是**原有的注解集合（原样，没加 serviceRole）**
     *       —— 于是这个端点对绝大多数真实服务是**静默 no-op**。</li>
     * </ul>
     * 也就是说 `serviceKey`（"关联的服务标识"）**几乎永远不会被真正写进去**。这是厂商如此，照抄。
     *
     * <p>随后把 **Deployment 对象**（即便走的是 StatefulSet 分支，序列化的仍是 Deployment——
     * 回退分支下 `v1Deployment` 为 null，`json.serialize(null)` 得到 `"null"`）序列化并整份
     * merge-patch；**任意异常**都回退到用**同一份 body** patch StatefulSet；
     * 回退也失败 ⇒ 400 `service.update.deployment.failed`。成功（含回退成功）返回 true。
     *
     * <p><b>未端到端验证</b>：会真改 K8s 工作负载，只用离线单测钉住报文内容 + 回退顺序。
     */
    boolean updateServiceRole(String namespace, String serviceKey, String name);

    /**
     * 设置/取消软件的服务入口（厂商 `POST /v1/software/{id}/service_entry?serviceId&port&type` →
     * `SoftwareController.setServiceEntry:492-499` + `SoftwareService.setServiceEntry:2238-2284`）。
     *
     * <p><b>四道"静默返回"闸</b>（厂商都是 `log.warn` 后 `return`，**不报错**，接口照样 200）：
     * <ol>
     *   <li>软件不存在；</li>
     *   <li>该软件**没有绑定**这个服务（拿 `serviceId` 参数去比 `g_software_app.app_name`）；</li>
     *   <li>K8s 里不存在该服务；</li>
     *   <li>`tenxCloud.service(clusterID, serviceName)` 返回的 Map 为空。</li>
     * </ol>
     * 注意参数名叫 `serviceId`，**实际是服务名**（照抄厂商的命名混乱）。
     *
     * <p>`type == 1`（设置入口）：
     * ① 把 (软件,服务,**port**) 那一行 `web_entry` 置 **true**；
     * ② 再执行厂商 `ServicePortMapper.updateServicePort` 那条 SQL——把该软件下**除那一行以外**的所有行
     * `web_entry` 置 **0**（即"全软件只有一个 web 入口"）；
     * ③ 把 `g_software.router_key` 设成 **`"/" + software.key + "/"`**、推进 `updated_at`。
     *
     * <p>`type == 0`（取消入口）：① 那一行 `web_entry` 置 **false**；② **不清其它行**；③ `router_key` 置 **null**。
     * <p>其它 type 值：两个 `if` 都不进 ⇒ 只通知网关。
     * <p>最后一律 `notificationGateway()`（异步、失败不影响响应）。
     */
    void setServiceEntry(String namespace, String softwareId, String serviceName, Integer port, Integer type);

    /**
     * 重置服务配置（厂商 `POST /v1/software/reset_service` → `AppService.resetService:539-570`）。
     *
     * <p>**破坏性**：把命名空间里**不带 `ingress-lb` 标签**的 Deployment **全部删除**（厂商循环里
     * `if (labels != null && labels.containsKey("ingress-lb")) continue;` 之外的全部删掉），
     * 任一删除抛 `ApiException` ⇒ 400 `service.reset.failed`；全部成功返回 `true`。
     *
     * <p><b>未照抄</b>：厂商在删 Deployment 之前还有一段基于时速云"App"概念的
     * `allPageApps()` + `removeApp()`（把"没有任何 `system/appName=gaop` 服务的 App"整个删掉）。
     * 本环境是原生 K8s，没有 App 这一层（见 `docs/findings/79`），所以只做 Deployment 那半段。
     */
    boolean resetService(String namespace);

    /**
     * 镜像存储大小（厂商 `GET /v1/service/imageStorage` → `ServiceController.getImageStorageInfo:2588-2595`）。
     *
     * <p>厂商先取 `nodeVersionService.getVersion()`（只打日志），再调
     * `imageServiceInstance.imageStorageInfo()`。**k3s 分支**（`K3sImageServiceImpl:128-171`）是：
     * SSH 到 `ssh.host` → `df -h <mount.path> | awk 'NR==2 {print $2, $4}'` → 把两列换算成**字节**
     * （`switchSize`：T/G/M 后缀分别乘 1024^4/1024^3/1024^2，**没有 K/B 分支**，因此 `df -h` 给出
     * 不带后缀的字节数时会得到 **0**）；
     * **连接失败或命令异常 ⇒ 返回 `{free:0,total:0}`（不报错）**。
     * <p>k8s（Harbor）分支走 Harbor API 的 `systemVolumeInfo`，本环境没有 Harbor（`findings/79`）⇒ 不做。
     *
     * <p><b>类 C：本环境不可验证</b>（需要能 SSH 的节点 + `df` 输出）。
     *
     * @param mountPath 厂商 `mount.path`（默认 `/data/paas/`）
     */
    com.csm.paas.core.domain.software.model.valobj.StorageInfoVO queryImageStorage(String mountPath);

    /**
     * 服务历史日志（厂商 `POST /v1/service/logs` → `ServiceController.serviceLogs:3931-3947` +
     * `TenxCloudImpl.getHistoryLog:159-235`）。
     *
     * <p>厂商把 body 拷成 `LogForm`（`serviceName/startTime/endTime/size/keyword`）后：
     * <ol>
     *   <li>`startTime`/`endTime` 为 null ⇒ 都取**当前时间**；`size` 为 null 或 &lt;0 ⇒ **1000**；</li>
     *   <li>时间跨度 &gt; **720 小时** ⇒ 400 `query.time.range.must.between.720`；</li>
     *   <li>拼 Loki `query_range` URL（`start`/`end` 是 **毫秒 × 1_000_000**）并 GET；</li>
     *   <li>`historyLog.success()` 为真才返回 `data`，否则 `Response.ok(null)` ⇒ **`data:null`**。</li>
     * </ol>
     *
     * <p>本方法只做**前两步 + 取原始 JSON**：解析成厂商 `HistoryLog` 形状由 trigger 层完成
     * （domain 不引 Jackson）。返回 **null** 表示"日志服务没有给出可用响应" ⇒ 控制器回 `data:null`。
     *
     * <p><b>类 C：本环境不可验证</b>（本机没有 Loki）。
     */
    String queryServiceHistoryLogJson(String serviceName, java.util.Date startTime, java.util.Date endTime,
                                      Integer size, String keyword);

    /**
     * 容器资源使用 Top5（厂商 `POST /v1/software/top?type&namespace` →
     * `SoftwareController.topPodResource:759-764` + `SoftwareService.topPodResource:1734-1873`）。
     *
     * <p>`namespace` 为空 ⇒ 用配置的命名空间；`type` 只影响**用哪条 PromQL 取榜单**：
     * `cpu` / `mem` / `netout` / `netin` / `diskread` / `diskwrite`；**其它值 ⇒ 榜单为空**
     * （厂商 `switch` 无 default）。
     *
     * <p>逐行照抄的两点：
     * <ol>
     *   <li>榜单出来之后，**每个** pod 都要把另外 5 个指标补齐（`getOtherType*`，各查一次 Prometheus），
     *       查不到就写 **字符串 `"0"`**；</li>
     *   <li>`cpu` 补齐时先算 `cpuNum`：读该 pod 的 `status.hostIP`，再在节点列表里找
     *       **InternalIP 相同**的节点，取其 `status.allocatable.cpu` 的**整数部分**；
     *       读 pod / 读节点失败都**只记日志**并用 **1**（照抄）。</li>
     * </ol>
     *
     * <p>响应每条恒为 **8 个键**（`podName / cpuUsageRate / memory / networkOutBound /
     * networkInBound / diskRead / diskWrite / namespace`），未取到的项是 `"0"` 而不是 null。
     *
     * <p><b>类 C：本环境不可验证</b>（需要 Prometheus / metrics-server）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.TopPodResourceInfoVO> queryTopPodResources(
            String type, String namespace);

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

    /**
     * 批量**重启**服务（厂商 `PUT /v1/service/batch-restart`，body 是服务名数组 →
     * `ServiceController.serviceRestart:3826-3912`，规格见 `docs/findings/127`）。
     *
     * <p>逐个服务：
     * <ol>
     *   <li>读 Deployment（读不到回退 StatefulSet；都没有 ⇒ 跳过）；</li>
     *   <li>**StatefulSet 分支**：给 pod 模板打注解 `kubectl.kubernetes.io/restartedAt` 后整份 replace，结束该服务；</li>
     *   <li>**Deployment 分支**：先按标签 `system/svcName` 找**第一个** pod 名（找不到就跳过"写记录"这步）→
     *       查 `g_pod_event` 里该 (服务, pod) 的历史重启次数，取 `max(1, 历史最大值)` →
     *       **先插一条 `g_pod_event`**，再打注解并整份 replace（厂商就是这个顺序：写库在前、replace 在后）；</li>
     *   <li>找 pod 名失败 ⇒ 400 `service.get.service.info.exception`；
     *       replace 失败 ⇒ 400 `service.restart.name.failed`（带服务名）。</li>
     * </ol>
     *
     * <p>注解值照抄厂商：**`LocalDateTime.now().toString()`**（不是毫秒）。
     * <p>入参为空 ⇒ 直接返回 true。
     */
    boolean batchRestartServices(String namespace, java.util.List<String> serviceNames);

    /**
     * kube-system 基础组件列表（厂商 `GET /v1/service/baseComponent?nodeName=` →
     * `ServiceController.getKubeSystemInfo:3218-3311`，规格见 `docs/findings/128`）。
     *
     * <p>三个组件固定顺序：`weave-net`、`kube-proxy`、`kube-keepalived-vip`。每项含两个键
     * `daemonSet`（DaemonSet 快照，读不到为 null）与 `v1PodList`（该节点上的 pod 快照，空则 null）。
     *
     * <p><b>标签选择器不一样</b>：`weave-net` 与 `kube-keepalived-vip` 用 `name=`，
     * 而 **`kube-proxy` 用 `k8s-app=`**（照抄）。
     *
     * <p><b>两处"静默"</b>（照抄）：① 任何 K8s 读失败都**只记日志、不报错**（所以这个端点不会 400）；
     * ② `kube-keepalived-vip` 的 pod 列表为空（或读取失败）时，**整个条目直接不出现**在响应里。
     *
     * @param nodeName 节点名（用于 `spec.nodeName` 过滤 pod）
     */
    java.util.List<java.util.Map<String, Object>> queryBaseComponents(String nodeName);

    /**
     * 软件总览（厂商 `POST /v1/software/overView` → `SoftwareService.getOverViewInfo:1631-1720`，
     * 规格见 `docs/findings/129`）。
     *
     * <p>三组计数，规则**各不相同**：
     * <ol>
     *   <li><b>software</b>：遍历全部 `g_software` 行 → 每行取其 `g_software_app` 的 app_name →
     *       逐个在 k8s 服务列表里找同名 Deployment：找到就用其 `status` 推状态串，**找不到算 `"STOP"`** →
     *       再按 `getSoftwareStatus` 汇总成 `STOP`/`RUNNING`/`PART_RUN` → `PART_RUN` 归到 **pending**；</li>
     *   <li><b>service</b>：遍历 k8s 服务列表，**过滤掉** `labels["system/appType"]` 以 `gaop` 开头的、
     *       以及（appType 为 null 时）`system/appName` 为 `gis-server`/`gis-jb`/`gaop` 的；其余按 status 计数；</li>
     *   <li><b>pod</b>：遍历命名空间全部 pod，**过滤** `labels["system/appName"] == "gaop"` 的；
     *       `phase` 为 `Pending`/`Terminating` ⇒ pending，`Running` ⇒ running，其它 ⇒ stop。
     *       列 pod 失败**只记日志**（pod 全 0，不影响另外两组）。</li>
     * </ol>
     */
    com.csm.paas.core.domain.software.model.entity.SoftwareOverviewEntity querySoftwareOverview(String namespace);

    /**
     * 检测软件或服务是否存在 ConfigMap 配置组（厂商 `POST /v1/software/existConfigmap` →
     * `SoftwareService.existConfigMapBySoftwareOrService:3834-3913`，规格见 `docs/findings/133`）。
     *
     * <p>`type == "software"`：逐个 `key`（软件 id）→ 取该软件的 `g_software_app` → 逐个 Deployment
     * 收集 `volumes[*].configMap.name`；`type == "service"`：逐个 `key`（Deployment 名）直接收集。
     * **只要有一个 key 收集到名字就返回 true**（边收集边判）。
     *
     * <p><b>一处主动收紧</b>（`deviations.md` B22）：厂商写的是 `key != null || key.size() != 0`——
     * 用 `||` 且后面直接调 `size()` ⇒ `key` 缺失时 **NPE ⇒ 500**；而且这让"不带 key ⇒ 检查全部软件"
     * 那个分支**永远不可达**。我们按"没有 key ⇒ 返回 false"处理（**不实现**那个死分支）。
     */
    boolean existConfigMap(String namespace, String type, java.util.List<String> keys);

    /**
     * 导出配置组（厂商 `POST /v1/software/exportConfigmap` →
     * `SoftwareService.exportConfigMapBySoftwareOrService:3915-4018` + `exportConfigMap:3453-3477`，
     * 规格见 `docs/findings/134`）。
     *
     * <p>收集 ConfigMap 名的那套逻辑与 {@link #existConfigMap} 相同，但**两处不同**：
     * <ol>
     *   <li>service 分支里 **Deployment 读不到 ⇒ 抛 400** `service.get.service.config.identifier.exception`
     *       （带 key），不是"跳过"；</li>
     *   <li>收集完之后**按名读 ConfigMap**（读不到的跳过），返回这些 ConfigMap 的快照供控制器写成 JSON 附件。</li>
     * </ol>
     *
     * <p>厂商"不带 key ⇒ 列全部 ConfigMap"那条路是**死代码**（`key != null || key.size() != 0` 的 `||` bug）；
     * 即便走到，它的 fieldSelector 是 `!ingress-lb`——**实测该 selector 非法**（API Server 返回 400），
     * 于是被 catch 成空列表 ⇒ 导出恒为 `[]`。我们按"没有 key ⇒ 空列表"处理（同 B22）。
     */
    java.util.List<java.util.Map<String, Object>> collectConfigMapsForExport(String namespace, String type,
                                                                             java.util.List<String> keys);

    /**
     * 更新"软件配置里的服务端口"（厂商 `POST /v1/software/servicePort` →
     * `SoftwareService.softwareServicePort:2836-2876`，规格见 `docs/findings/136`）。
     *
     * <p>规则：
     * <ol>
     *   <li>`serviceName` 取**第一条**的（form 为空 ⇒ 厂商 `IndexOutOfBounds` ⇒ 500，我们不额外兜）；</li>
     *   <li>按 `g_software_app.app_name = serviceName` 找软件；**一个都没有 ⇒ 直接返回 true**（不做事）；</li>
     *   <li>对每个软件：先看旧的 `web_entry = true` 那一行的 port——**若它还在新列表里**，
     *       新的那一行继续标 `web_entry = true`（其余行 false）；</li>
     *   <li>**先删光**该软件+该服务的端口映射，再按 `containerPort` 列表逐行插入，
     *       `path_name = serviceName + "-" + port`、`updated_at = 同一次调用取的时间`、**不写 `allow`（NULL）**；</li>
     *   <li>最后通知网关刷新（`IGatewayRefreshPort`，与 `patchService` B 分支同一个端口）。</li>
     * </ol>
     */
    boolean updateSoftwareServicePorts(java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePortItemVO> form);

    /**
     * 已被占用的 NodePort 列表（厂商 `GET /v1/pub/nodePortUsed?port=` →
     * `UserService.getNodePortUsedList:2120-2240`，规格见 `docs/findings/141`）。
     *
     * <p>三段来源：① 带非空 `ingress-lb` 标签的 Deployment 的 `httpPort`/`httpsPort` **注解**
     * （各一条，**两条都叫"框架内部"**）；② 该标签值按 `-` 拆开取**第 2 段**当 ingress 名，
     * 读 `tcp-services-<名>`/`udp-services-<名>` 两个 ConfigMap 的 data
     * （`key=nodePort`、`value=<proto>/<serviceName>` ⇒ `{name=serviceName, port=key}`）；
     * ③ `type=NodePort` 的 Service ⇒ `{name = <标签 name 或 app> + ":" + <service port>, port = <nodePort>}`。
     *
     * <p>`port` 非空时只保留 port 相等的条目；第 ③ 段读失败 ⇒ 记日志并**直接返回**（保留前两段）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.NodePortUsedVO> queryNodePortUsed(
            String namespace, Integer port);

    /**
     * 配置组列表（厂商 `GET /v1/config_map?page=&size=&keyword=` →
     * `ConfigMapController.listConfigMap:185-243`，规格见 `docs/findings/142`）。
     *
     * <p>规则：
     * <ol>
     *   <li>列 ConfigMap 时用**标签选择器 `!ingress-lb`**（"该标签不存在"，**合法语法**）；</li>
     *   <li>再**硬编码排除**两个名字：`ingress-nginx-controller`、`kube-root-ca.crt`；</li>
     *   <li>`keyword` 非空白 ⇒ 先只去掉**空格**，再按**正则 `(.*)<keyword>(.*)` 匹配名字**；
     *       **匹配为空 ⇒ `items` 置 null**；</li>
     *   <li>分页用 `skip((page-1)*size).limit(size)`；</li>
     *   <li><b>`total` 恒为"排除两个名字之后、关键字过滤之前"的总数</b>——即使带了关键字，
     *       `total` 也不反映命中数（厂商如此，照抄）；</li>
     *   <li>任何异常 ⇒ 500 `configmap.get.list.failed`。</li>
     * </ol>
     */
    com.csm.paas.core.domain.software.model.entity.ConfigMapListEntity queryConfigMapList(
            String namespace, int page, int size, String keyword);

    /**
     * 配置组是否存在（厂商 `GET /v1/config_map/{name}/exist` → `isExistConfigmap:98-110`）。
     * <p>读得到 ⇒ true；**404 ⇒ false**；其它 K8s 错误 ⇒ 500 `configmap.system.error`。
     */
    boolean configMapExists(String namespace, String name);

    /**
     * 配置组详情（厂商 `GET /v1/config_map/{name}` → `configmap:155-182`）。
     *
     * <p>`name` 空白 ⇒ 400 `configmap.name.must.not.empty`；不匹配 `[-._a-zA-Z0-9]+` ⇒
     * 400 `configmap.name.not.invalid`；读不到（404）⇒ 400 `configmap.not.exist`（带 name）；
     * 其它 K8s 错误 ⇒ 500 `configmap.find.configmap.api.failed`。
     */
    java.util.Map<String, Object> queryConfigMap(String namespace, String name);

    /**
     * 配置组关联的服务（厂商 `GET /v1/config_map/relationService?name=` →
     * `configMapRelationService:363-432`，规格见 `docs/findings/143`）。
     *
     * <p>把 ConfigMap 的 **data key 当作 subPath**，扫描所有 Deployment 的卷挂载：
     * 卷的 `configMap.name` 命中且 `volumeMount.subPath` 命中某个 key ⇒
     * 记一条 `{serviceName=<Deployment 名>, mountPath}`。结果按 `configMapName`（即 key）排序。
     *
     * <p>ConfigMap 读不到 ⇒ **返回空列表**（200，不报错）。
     * <p>**一处主动简化**（`deviations.md` B23）：厂商开头还会列 Node 取 master ip，但那个 ip
     * **只用于打日志**、不影响响应；我们省略这次调用（差异只在"列 Node 失败 ⇒ 厂商提前返回空列表"这条错误路径上）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.entity.ConfigMapInfoEntity> queryConfigMapRelations(
            String namespace, String name);

    /**
     * 建配置组（厂商 `POST /v1/config_map` → `createConfigMap:122-152`）。
     *
     * <p>`name` 空白 ⇒ 400 `configmap.name.must.not.empty`；不匹配 `[-._a-zA-Z0-9]+` ⇒
     * 400 `configmap.name.not.invalid`；**已存在 ⇒ 400 `configmap.existed`（带 name）**；
     * 建失败 ⇒ 500 `configmap.create.failed`。body **只有 `metadata.name`**（不带 data）。
     */
    java.util.Map<String, Object> createConfigMap(String namespace, String name);

    /**
     * 新增/覆盖配置组里的一个配置项（厂商 `POST /{name}/config` → `createConfig:276-315`）。
     *
     * <p>`form.name` 空白/非法 ⇒ 两个 400；ConfigMap 不存在（404）⇒ 400 `configmap.not.exist`；
     * ConfigMap 读取失败（非 404）⇒ 500 `configmap.get.config.error`（带 ConfigMap 名）；
     * `value` 为空 ⇒ **存空串**；同名**覆盖**；replace 失败 ⇒ 500 `configmap.request.api.error`。
     */
    java.util.Map<String, Object> upsertConfigEntry(String namespace, String configMapName,
                                                    String entryName, String value);

    /**
     * 删除配置组里的一个配置项（厂商 `DELETE /{name}/config/{configName}` → `deleteConfig:246-274`）。
     *
     * <p>与上面同款的前置校验；**配置项不存在 ⇒ 400 `configmap.name.not.exist`（带配置项名）**。
     */
    java.util.Map<String, Object> deleteConfigEntry(String namespace, String configMapName, String entryName);

    /**
     * 删配置组（厂商 `DELETE /v1/config_map?names=` → `deleteConfigMap:337-358`）。
     *
     * <p>`names` 按 `,` 拆开、**每个名字 trim 后删**，但**返回列表里放的是未 trim 的原值**；
     * 删除失败/不存在 ⇒ 只记日志（不报错）；返回成功删除的那些名字。
     */
    java.util.List<String> deleteConfigMaps(String namespace, String names);

    /**
     * 导出配置组（厂商 `POST /v1/config_map/backup` → `SoftwareService.exportConfigMap:3453-3477`）。
     *
     * <p>`nameList` 非空 ⇒ **按名读**（读不到的**跳过**）；为空/null ⇒ 列**不带 `ingress-lb` 标签**的
     * 全部 ConfigMap（厂商的 `labelSelector = "!ingress-lb"`）。响应是 JSON 附件。
     */
    java.util.List<java.util.Map<String, Object>> exportConfigMaps(String namespace, java.util.List<String> nameList);

    /**
     * 导出 tcp/udp 监听配置（厂商 `POST /v1/config_map/listenBackup` →
     * `SoftwareService.exportTcpAndUdpConfigMap:3739-3774`）。
     *
     * <p>列**带 `ingress-lb` 标签**的 ConfigMap；对每个：标签值为 **`"true"` 的跳过**，
     * 否则按 `-` 拆开取第 2 段当作 ingress 名，若名字等于 `tcp-services-<段>`/`udp-services-<段>`
     * 就分别填到 `tcpListen`/`udpListen`。`ingressList` **恒为 null**。
     */
    com.csm.paas.core.domain.software.model.entity.ListenBackInfoEntity exportListenConfigs(String namespace);

    /**
     * 导入配置组（厂商 `POST /v1/config_map/import` → `SoftwareService.importConfigMap:3363-3403`）。
     *
     * <p>逐个对象：先读同名 ConfigMap 判断是否存在 ⇒ 存在用 **replace**、不存在用 **create**；
     * 两种失败**都只记日志**（继续下一个）；替换/创建前把 `metadata.resourceVersion` 与
     * `metadata.uid` **置 null**。**恒返回 true**。
     *
     * <p>厂商在失败时还会写"恢复日志"（`BackupLogService.sendRecoveryLog`），但那受静态开关
     * `RECOVERY` 控制、**默认为 false**，且只往内存列表追加 + 推 `/topic/recoveryLog`；我们不做。
     */
    boolean importConfigMaps(String namespace, java.util.List<java.util.Map<String, Object>> configMaps);

    /**
     * 导入 tcp/udp 监听配置（厂商 `POST /v1/config_map/importListen` →
     * `SoftwareService.importListenConfig:3590-3711`）。
     *
     * <p><b>破坏性</b>：会 **replace**（不存在则创建）`tcp-services-<suffix>` / `udp-services-<suffix>`
     * 两个 ConfigMap。
     *
     * <p>照抄规则（含怪点）：
     * <ol>
     *   <li>先列 `ingress-lb` 标签的 ConfigMap，取**第一个标签值 != "true"** 的当 `ingressName`
     *       （列表读失败 ⇒ 400 `k8s.get.cluster.lb.info.failed`）；</li>
     *   <li>`suffixName = ingressName.split("-")[1]`（没有 `-` ⇒ **越界异常 500**，照抄）；</li>
     *   <li>读 `ingressName` 这个 **Service**，把名字为 `port-0`/`port-1`/`port-status`/`port-health`
     *       的端口收进一个局部 `ports` 列表——**这个列表之后完全没被使用（厂商死代码）**，
     *       但它带来的 NPE 是可观测的：`ingressName` 为 null 或 Service 读不到 ⇒ `getSpec()` 为 null
     *       ⇒ **NPE 500**；`spec.ports` 为 null 同理。照抄（仍要读、仍要遍历）；</li>
     *   <li>`tcpListen`：`data` 为 null ⇒ **直接 return（连 udp 也不处理）**；否则每个 entry
     *       的 key 当端口名 `port-tcp-<key>`，value 按 `":"` 拆两段取第 2 段当 port 与 targetPort
     *       （**没有冒号 ⇒ 越界 500**，照抄），协议写死 `TCP`；</li>
     *   <li>然后把该 ConfigMap 改名：原名按 `-` 拆开取前两段 + `-<suffixName>`；
     *       `resourceVersion`/`uid` 置 null；labels 里写 `ingress-lb=ingressName`；
     *       replace（**失败只记日志**）；</li>
     *   <li>`udpListen` 同 tcp（协议 `UDP`、名前缀 `port-udp-`）；</li>
     *   <li>控制器恒返回 `true`。</li>
     * </ol>
     */
    boolean importListenConfig(String namespace,
            com.csm.paas.core.domain.software.model.valobj.ListenImportVO listenImport);
}
