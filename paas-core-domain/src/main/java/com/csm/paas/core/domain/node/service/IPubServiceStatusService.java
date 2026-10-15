package com.csm.paas.core.domain.node.service;

import com.csm.paas.core.domain.node.model.valobj.PubServiceStatusVO;

import java.util.List;
import java.util.Map;

/**
 * 公共 API - 服务运行状态（厂商 `controller/pub/PublicController.java` 的两条）。
 *
 * <p>放在 `domain/node` 下**是因为本次改动的文件白名单**只放行 `domain/cicd/**`、`domain/cluster/**`、
 * `domain/node/**`（任务约束第 3 条），并不是说它属于节点域——它读的是 `gaop-namespace` 的工作负载。
 * （同一约定见 {@code IPubServiceQueryService} 的类注释。）
 */
public interface IPubServiceStatusService {

    /**
     * 厂商 `PublicController.allServiceStatus:442-459`（`GET /v1/pub/service_status`）。
     *
     * <p>厂商数据源是 `tenxCloud.allPageService(null)`（TCE 开放 API，**类 C：本环境不可达**）；
     * 本平台改用**同集群 Deployment 直读**等价复刻——这与 `ModeServiceImpl.getListWithOutGaop`
     * 的既有替代口径一致（见 `IServiceRuntimePort` 末尾那段说明）。
     *
     * <p>`status` 逐字照抄 `utils/TenxUtils.getStatus:49-74`，**连 `STARING` 这个拼写错误一起**：
     * `conditions == null` ⇒ `STOP`；`readyReplicas == null` ⇒ `STOP`；`readyReplicas > 0` ⇒ `RUNNING`；
     * 否则 `progressing && !available` ⇒ `STARING`，`progressing` ⇒ `RUNNING`，否则 `STARING`。
     */
    List<PubServiceStatusVO> allServiceStatus();

    /**
     * 厂商 `PublicController.getServiceStatusInfo:2428-2585`（`GET /v1/pub/serviceStatus`）。
     *
     * <p>返回的 Map 直接就是厂商的 JSON（键名与顺序照抄 `controller/response/ServiceStatusInfo`）：
     * <pre>
     * {
     *   "appstatus": {"deployments": [...], "pods": [...]},
     *   "sysInfo":   {"k8s": {...}, "dns": {...}, "monitor": {...}, "apiserver": {...}, "etcd": {...}},
     *   "radondbInfo": {"podList": [...], "statefulSet": {...}} | null
     * }
     * </pre>
     * 注意三处照抄点：
     * <ol>
     *   <li>字段名是 **`appstatus`**（小写 s）——厂商用 Jackson 的 JsonProperty 覆盖了字段名
     *       （`ServiceStatusInfo.java:13`），而 `sysInfo` 没有覆盖；</li>
     *   <li>`sysInfo` 里**没有 `logging` 键**——厂商声明了 `loggingName = "logging"` 却从未 `put`（死代码，
     *       `PublicController:2469-2470`）；</li>
     *   <li>`getSystemInfo` 命不中 pod 时 `status` 保持 **null**（不是 "error"），
     *       命中了但容器未 ready 才是 `"error"`（`PublicController:2627-2635`）。</li>
     * </ol>
     */
    Map<String, Object> serviceStatusInfo();
}
