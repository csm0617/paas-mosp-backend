package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.cluster.adapter.port.IPubClusterPort;
import com.csm.paas.core.domain.node.model.valobj.PubServiceStatusVO;
import com.csm.paas.core.domain.node.service.IPubServiceStatusService;
import com.csm.paas.core.types.exception.K8sApiException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务运行状态的两条公共端点（厂商 `PublicController.allServiceStatus:442-459` +
 * `getServiceStatusInfo:2428-2614`）。
 *
 * <p><b>类 C：本环境不可验证</b>——厂商这条读的是 TCE 开放 API（`tenxCloud.allPageService`）
 * 与集群 pod 的实时状态；本平台按既有口径用 Deployment/pod 直读替代。
 * <p>纯函数部分（`TenxUtils.getStatus`、`getSystemInfo`、`convertToPods`）是**逐行照抄**的，
 * 可以离线断言。
 */
@Slf4j
@Service
public class PubServiceStatusServiceImpl implements IPubServiceStatusService {

    /** 厂商 `K8s.NAMESPACE` / `tenxOpenApiConfig.getTeamSpace()`，本仓统一取 `gaop.k8s.namespace`。 */
    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    /** 厂商配置项 `radondb.name`（vendor `application.yml:165` 默认 `gaop`）。 */
    @Value("${radondb.name:gaop}")
    private String radondbName;

    /** 厂商配置项 `gis.url`（vendor `application.yml:122` 默认 `gis-server:8080`）。 */
    @Value("${gis.url:gis-server:8080}")
    private String gisUrlInfo;

    @Resource
    private IPubClusterPort pubClusterPort;

    // ------------------------------------------------------------------ service_status

    /** 厂商 `PublicController.allServiceStatus:442-459` + `TenxUtils.getStatus:49-74`。 */
    @Override
    public List<PubServiceStatusVO> allServiceStatus() {
        List<PubServiceStatusVO> serviceList = new ArrayList<>();
        for (Map<String, Object> deployment : pubClusterPort.listDeploymentSnapshots(namespace)) {
            PubServiceStatusVO service = new PubServiceStatusVO();
            service.setServiceName(metadataName(deployment));
            service.setStatus(getStatus(asMap(deployment.get("status"))));
            serviceList.add(service);
        }
        return serviceList;
    }

    /**
     * 厂商 `TenxUtils.getStatus:49-74` 的逐行照抄。
     * <p>照抄点：返回的是 **`STARING`**（厂商把 STARTING 拼错，`:70`、`:74`）——不要"顺手改对"。
     */
    private static String getStatus(Map<String, Object> status) {
        if (status == null) {
            return null;
        }
        List<Map<String, Object>> conditions = asMapList(status.get("conditions"));
        boolean progressing = false;
        boolean available = false;
        if (conditions == null) {
            return "STOP";
        }
        for (Map<String, Object> condition : conditions) {
            if ("Progressing".equals(condition.get("type"))) {
                progressing = "True".equals(condition.get("status"));
                continue;
            }
            if (!"Available".equals(condition.get("type"))) {
                continue;
            }
            available = "True".equals(condition.get("status"));
        }
        Integer readyReplicas = asInteger(status.get("readyReplicas"));
        if (readyReplicas == null) {
            return "STOP";
        }
        if (readyReplicas > 0) {
            return "RUNNING";
        }
        if (progressing && !available) {
            return "STARING";
        }
        return progressing ? "RUNNING" : "STARING";
    }

    // ------------------------------------------------------------------ serviceStatus

    /** 厂商 `PublicController.getServiceStatusInfo:2428-2614`。 */
    @Override
    public Map<String, Object> serviceStatusInfo() {
        String radondbStatefulSet = radondbName + "-radondb-mysql-";
        // 厂商 `K8sApiClientManager.getPodList(null)`：全命名空间 pod；读失败会抛（500）
        List<Map<String, Object>> podList = pubClusterPort.listAllPodSnapshots();

        Map<String, Object> infoHashMap = new LinkedHashMap<>();
        infoHashMap.put("k8s", sysInfo("1.14.3", "Running"));
        infoHashMap.put("dns", sysInfo("", getSystemInfoStatus(podList, "k8s-app", "kube-dns", "kube-system")));
        infoHashMap.put("monitor",
                sysInfo("v0.34.0", getSystemInfoStatus(podList, "name", "monitor-service", "system-tce")));
        infoHashMap.put("apiserver",
                sysInfo("v1.0", getSystemInfoStatus(podList, "component", "kube-apiserver", "kube-system")));
        infoHashMap.put("etcd",
                sysInfo("v1.0", getSystemInfoStatus(podList, "component", "etcd", "kube-system")));
        // 照抄：厂商声明了 loggingName = "logging" 却从未 infoHashMap.put（死代码）

        List<Map<String, Object>> allDeployments;
        try {
            allDeployments = pubClusterPort.listDeploymentSnapshots(namespace);
        } catch (K8sApiException e) {
            // 厂商 `catch (ApiException) { log.error; return Response.ok(serviceStatusInfo) }`
            // 此刻 appstatus 与 radondbInfo 都还是 null
            log.error("获取所有的deployment出现异常，异常为:{}", e.getMessage());
            Map<String, Object> partial = new LinkedHashMap<>();
            partial.put("appstatus", null);
            partial.put("sysInfo", infoHashMap);
            partial.put("radondbInfo", null);
            return partial;
        }

        List<Map<String, Object>> gaopDeployment = new ArrayList<>();
        for (Map<String, Object> item : allDeployments) {
            try {
                Map<String, String> labels = asStringMap(asMap(item.get("metadata")).get("labels"));
                String appName = labels.get("system/appName");
                if ("gaop".equals(appName)) {
                    gaopDeployment.add(item);
                }
            } catch (NullPointerException ignored) {
                // 厂商这里显式 `catch (NullPointerException ignored) { return false; }`
            }
        }

        // gis：按 `gis.url` 的主机名去读一个 Deployment，读到就追加到列表尾部
        String gis = gisUrlInfo.replace("http://", "").replace("https://", "");
        String gisName = gis.split(":")[0];
        Map<String, Object> gisV1Deployment = pubClusterPort.readDeploymentSnapshot(namespace, gisName);
        if (gisV1Deployment != null) {
            gaopDeployment.add(gisV1Deployment);
        } else {
            log.error("无法获取带gis服务的deployment信息");
        }

        List<Map<String, Object>> deployments = new ArrayList<>();
        List<Map<String, Object>> pods = new ArrayList<>();
        for (Map<String, Object> deployment : gaopDeployment) {
            Map<String, Object> metadata = asMap(deployment.get("metadata"));
            String serviceName = asString(metadata.get("name"));

            Map<String, Object> metadataOut = new LinkedHashMap<>();
            metadataOut.put("name", serviceName);
            metadataOut.put("annotations", metadata.get("annotations"));
            metadataOut.put("generation", asLong(metadata.get("generation")));

            Map<String, Object> specOut = new LinkedHashMap<>();
            specOut.put("replicas", asInteger(asMap(deployment.get("spec")).get("replicas")));

            Map<String, Object> status = asMap(deployment.get("status"));
            Map<String, Object> statusOut = new LinkedHashMap<>();
            statusOut.put("availableReplicas", asInteger(status.get("availableReplicas")));
            statusOut.put("observedGeneration", asLong(status.get("observedGeneration")));
            statusOut.put("readyReplicas", asInteger(status.get("readyReplicas")));
            statusOut.put("replicas", asInteger(status.get("replicas")));
            statusOut.put("unavailableReplicas", asInteger(status.get("unavailableReplicas")));
            statusOut.put("updatedReplicas", asInteger(status.get("updatedReplicas")));

            Map<String, Object> deploymentInfo = new LinkedHashMap<>();
            deploymentInfo.put("metadata", metadataOut);
            deploymentInfo.put("spec", specOut);
            deploymentInfo.put("status", statusOut);
            deployments.add(deploymentInfo);

            for (Map<String, Object> pod : podList) {
                Map<String, Object> podMeta = asMap(pod.get("metadata"));
                if (!namespace.equals(podMeta.get("namespace"))) {
                    continue;
                }
                Map<String, String> labelmap = asStringMap(podMeta.get("labels"));
                if (labelmap == null) {
                    continue;
                }
                if (labelmap.containsKey("name") && labelmap.get("name").equals(serviceName)) {
                    pods.add(convertToPods(pod));
                }
            }
        }

        // radondb：pod 名以 `<radondb.name>-radondb-mysql-` 开头的也塞进 pods
        for (Map<String, Object> pod : podList) {
            Map<String, Object> podMeta = asMap(pod.get("metadata"));
            if (!namespace.equals(podMeta.get("namespace"))) {
                continue;
            }
            String podName = asString(podMeta.get("name"));
            if (podName != null && podName.startsWith(radondbStatefulSet)) {
                pods.add(convertToPods(pod));
            }
        }

        Map<String, Object> tenxServiceInfo = new LinkedHashMap<>();
        tenxServiceInfo.put("deployments", deployments);
        tenxServiceInfo.put("pods", pods);

        Map<String, Object> radondbInfoOut = null;
        List<String> radondbMysqlPod = new ArrayList<>();
        for (Map<String, Object> pod : podList) {
            Map<String, Object> podMeta = asMap(pod.get("metadata"));
            if (!namespace.equals(podMeta.get("namespace"))) {
                continue;
            }
            String generateName = asString(podMeta.get("generateName"));
            if (generateName == null || !generateName.equals(radondbStatefulSet)) {
                continue;
            }
            String podName = asString(podMeta.get("name"));
            log.info("获取的容器名称为:" + podName);
            radondbMysqlPod.add(podName);
        }
        if (!radondbMysqlPod.isEmpty()) {
            List<Map<String, Object>> v1PodList = new ArrayList<>();
            for (String name : radondbMysqlPod) {
                for (Map<String, Object> pod : podList) {
                    Map<String, Object> podMeta = asMap(pod.get("metadata"));
                    if (!namespace.equals(podMeta.get("namespace"))) {
                        continue;
                    }
                    if (name.equals(asString(podMeta.get("name")))) {
                        v1PodList.add(pod);
                        break;
                    }
                }
            }
            String statefulSetName = radondbName + "-radondb-mysql";
            Map<String, Object> statefulSet = null;
            try {
                statefulSet = pubClusterPort.readStatefulSetSnapshot(namespace, statefulSetName);
            } catch (K8sApiException e) {
                log.error("读取StatefulSet名称:" + statefulSetName + "失败，异常信息为：", e);
            }
            radondbInfoOut = new LinkedHashMap<>();
            radondbInfoOut.put("podList", v1PodList);
            radondbInfoOut.put("statefulSet", statefulSet);
        }

        // 字段顺序照抄 `ServiceStatusInfo` 的**声明顺序**：appstatus → sysInfo → radondbInfo
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appstatus", tenxServiceInfo);
        result.put("sysInfo", infoHashMap);
        result.put("radondbInfo", radondbInfoOut);
        return result;
    }

    /**
     * 厂商 `PublicController.getSystemInfo:2616-2637`：按"命名空间 + 标签"找**第一个** pod，
     * 有容器状态就按 ready 给 `Running`/`error`；**找不到就返回 null**
     * （厂商那边是"`SysInfo.status` 保持 null"，由调用方连 `version` 一起组装）。
     */
    private static String getSystemInfoStatus(List<Map<String, Object>> allPods, String labelKey,
                                             String serviceName, String namespace) {
        for (Map<String, Object> pod : allPods) {
            Map<String, Object> meta = asMap(pod.get("metadata"));
            if (!namespace.equals(meta.get("namespace"))) {
                continue;
            }
            Map<String, String> labels = asStringMap(meta.get("labels"));
            if (labels != null && labels.containsKey(labelKey) && serviceName.equals(labels.get(labelKey))) {
                List<Map<String, Object>> containerStatuses =
                        asMapList(asMap(pod.get("status")).get("containerStatuses"));
                if (containerStatuses != null && !containerStatuses.isEmpty()) {
                    Boolean ready = asBoolean(containerStatuses.get(0).get("ready"));
                    if (Boolean.TRUE.equals(ready)) {
                        return "Running";
                    }
                    return "error";
                }
                break;
            }
        }
        return null;
    }

    /**
     * 厂商 `PublicController.convertToPods:2587-2614`。
     *
     * <p>照抄点：`containerStatuses` 列表**无条件 add 一个元素**——即便原 pod 没有容器状态，
     * 也会得到一个 `{"restartCount":0,"state":null}`；且 `restartCount` 与 `state` 只在
     * 第一个容器状态存在且处于 running 时才被赋值。
     */
    private static Map<String, Object> convertToPods(Map<String, Object> pod) {
        Map<String, Object> podMeta = asMap(pod.get("metadata"));
        Map<String, Object> podStat = asMap(pod.get("status"));

        Map<String, Object> metadataOut = new LinkedHashMap<>();
        metadataOut.put("name", asString(podMeta.get("name")));

        Map<String, Object> statuses = new LinkedHashMap<>();
        statuses.put("restartCount", 0);

        List<Map<String, Object>> containerStatuses = asMapList(podStat.get("containerStatuses"));
        if (containerStatuses != null && !containerStatuses.isEmpty()) {
            Map<String, Object> first = containerStatuses.get(0);
            Map<String, Object> state = asMap(first.get("state"));
            if (state != null && asMap(state.get("running")) != null) {
                Map<String, Object> runningOut = new LinkedHashMap<>();
                runningOut.put("startedAt", asMap(state.get("running")).get("startedAt"));
                Map<String, Object> stateOut = new LinkedHashMap<>();
                stateOut.put("running", runningOut);
                statuses.put("state", stateOut);
                statuses.put("restartCount", asInteger(first.get("restartCount")));
            }
        }
        List<Map<String, Object>> containerStatusesOut = new ArrayList<>();
        containerStatusesOut.add(statuses);

        Map<String, Object> statusOut = new LinkedHashMap<>();
        statusOut.put("containerStatuses", containerStatusesOut);
        statusOut.put("phase", podStat.get("phase"));

        Map<String, Object> podOut = new LinkedHashMap<>();
        podOut.put("metadata", metadataOut);
        podOut.put("status", statusOut);
        return podOut;
    }

    /** 厂商 `SysInfo` 的字段声明顺序是 **version 在前、status 在后**。 */
    private static Map<String, Object> sysInfo(String version, String status) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", version);
        info.put("status", status);
        return info;
    }

    private static String metadataName(Map<String, Object> snapshot) {
        return asString(asMap(snapshot.get("metadata")).get("name"));
    }

    // ------------------------------------------------------------------ Map 取值小工具

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> asStringMap(Object o) {
        return (Map<String, String>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object o) {
        return (List<Map<String, Object>>) o;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer asInteger(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static Boolean asBoolean(Object o) {
        return o instanceof Boolean b ? b : null;
    }
}
