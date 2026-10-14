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
}
