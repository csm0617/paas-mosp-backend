package com.csm.paas.core.domain.software.service;

/**
 * 软件的启停与状态（厂商 `SoftwareController` 的 `GET /{id}/start`、`/{id}/stop`、`/{id}/status`，
 * `:518-660`）。
 *
 * <p>三个端点都是**两条分支**：
 * <ul>
 *   <li>`framework == "CS"` ⇒ 走 **SSH**（`ISshPort`）：`status` 是 `pgrep -f <execName>`、
 *       `start` 是 `nohup` 启动、`stop` 是 `pkill -f`；</li>
 *   <li>否则（BS 等）⇒ 走 **K8s**：把该软件的服务列表按状态筛一遍，
 *       `start` 启动所有非 `RUNNING` 的、`stop` 停掉所有非 `STOP` 的，然后按厂商
 *       `getSoftwareStatus` 折算总状态（`STOP` / `RUNNING` / `PART_RUN`）。</li>
 * </ul>
 *
 * <p><b>照抄点</b>：
 * <ul>
 *   <li>CS 分支里 `uploadStoreRecord == null` 或 `execName` 空白 ⇒ **直接返回 STOP**（`start`）／
 *       返回 STOP（`status`）；`stop` 的 CS 分支没有 uploadStoreRecord 判空；</li>
 *   <li>`start` 的 CS 分支在"已经在跑"时**先返回 RUNNING 不重复启动**；</li>
 *   <li>BS 分支的 `if (serviceList.isEmpty() || serviceList != null)` 是恒真写法（照抄）；
 *       过滤后列表为空时 `start` 返回 RUNNING、`stop` 返回 STOP；</li>
 *   <li>`start` 里 K8s 抛异常 ⇒ 400 `software.start.bs.service.failed`；`stop` 没有这层包装。</li>
 * </ul>
 *
 * <p><b>类 C</b>：CS 分支依赖 SSH，本环境无节点可连 ⇒ 代码齐全、不做在线验证。
 */
public interface ISoftwareLifecycleService {

    /** `GET /v1/software/{id}/status`：返回 `STOP` / `RUNNING` / `PART_RUN`。 */
    String queryStatus(String namespace, String softwareId);

    /** `GET /v1/software/{id}/start`。 */
    String start(String namespace, String softwareId);

    /** `GET /v1/software/{id}/stop`。 */
    String stop(String namespace, String softwareId);
}
