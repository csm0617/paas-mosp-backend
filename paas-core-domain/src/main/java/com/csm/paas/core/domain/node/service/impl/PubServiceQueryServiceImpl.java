package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.node.service.IPubServiceQueryService;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务标识列表实现（厂商 `controller/pub/PublicController.java:2029-2068`）。
 *
 * <p>命名空间取值口径与仓库既有约定一致：`gaop.k8s.namespace`，默认 `gaop-namespace`
 * （厂商 `tenx.open-api.team-space`，`application.yml:66`）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PubServiceQueryServiceImpl implements IPubServiceQueryService {

    private final IServiceRuntimePort serviceRuntimePort;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    @Override
    @SuppressWarnings("unchecked")
    public List<String> serviceKeyList(String name, String serviceName) {
        String keyword = null;
        if (isNotBlank(serviceName)) {
            keyword = serviceName.replaceAll(" +", "");
        }
        List<Map<String, Object>> deploymentList;
        try {
            deploymentList = serviceRuntimePort.listDeploymentSnapshots(namespace);
        } catch (RuntimeException e) {
            log.error("获取gaop-namespace下的服务失败", e);
            throw new WrongArgumentException("service.get.service.name.list.error");
        }
        List<String> serviceList = new ArrayList<>();
        for (Map<String, Object> item : deploymentList) {
            Map<String, Object> metadata = asMap(item.get("metadata"));
            if (metadata == null) {
                continue;
            }
            Map<String, Object> labels = asMap(metadata.get("labels"));
            Object appName = labels == null ? null : labels.get("system/appName");
            if (appName == null || "gaop".equals(appName)) {
                continue;
            }
            Object n = metadata.get("name");
            if (n == null || n.equals(name)) {
                continue;
            }
            serviceList.add(String.valueOf(n));
        }
        List<String> results = new ArrayList<>();
        if (!serviceList.isEmpty()) {
            if (keyword != null) {
                Pattern pattern = Pattern.compile(keyword);
                for (String service : serviceList) {
                    Matcher matcher = pattern.matcher(service);
                    if (!matcher.find()) {
                        continue;
                    }
                    results.add(service);
                }
            } else {
                results.addAll(serviceList);
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
