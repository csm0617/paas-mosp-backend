package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/config_map`（厂商 `ConfigMapController.listConfigMap:185-243`）。
 * <p>规格见 `docs/findings/142`。锁住：硬编码排除两个名字、`keyword` 只去空格 + 正则匹配、
 * **匹配为空 ⇒ items=null**、以及最反直觉的一条——**`total` 恒为关键字过滤之前的数量**。
 */
class ConfigMapListTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static Map<String, Object> cm(String name) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        Map<String, Object> m = new HashMap<>();
        m.put("metadata", metadata);
        return m;
    }

    private void listOf(String... names) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String n : names) {
            items.add(cm(n));
        }
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("apiVersion", "v1");
        snapshot.put("kind", "ConfigMapList");
        snapshot.put("metadata", Map.of("resourceVersion", "123"));
        snapshot.put("items", items);
        when(port.listConfigMapsWithoutIngressLb("ns")).thenReturn(snapshot);
    }

    @Test
    void hardcodedNamesAreExcluded() {
        listOf("kube-root-ca.crt", "ingress-nginx-controller", "a", "b");

        var result = service().queryConfigMapList("ns", 1, 20, null);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getItems().size());
        assertEquals("v1", result.getApiVersion());
        assertEquals("ConfigMapList", result.getKind());
        assertEquals("123", result.getMetadata().get("resourceVersion"));
    }

    @Test
    void pagingSlicesButTotalStaysFull() {
        listOf("a", "b", "c", "d", "e");

        var result = service().queryConfigMapList("ns", 2, 2, null);

        assertEquals(5, result.getTotal(), "total 是全量（未分页）");
        assertEquals(2, result.getItems().size());
        assertEquals("c", ((Map<?, ?>) result.getItems().get(0).get("metadata")).get("name"));
    }

    @Test
    void keywordMatchesByNameRegexAndTotalIgnoresIt() {
        listOf("alpha", "beta", "gamma");

        var result = service().queryConfigMapList("ns", 1, 20, "a");

        // 匹配到 alpha/beta/gamma（都含 a）⇒ 3 条；total 仍是 3（这里两者恰好相同）
        assertEquals(3, result.getTotal());
        assertEquals(3, result.getItems().size());

        var onlyAlpha = service().queryConfigMapList("ns", 1, 20, "alpha");
        assertEquals(1, onlyAlpha.getItems().size());
        assertEquals(3, onlyAlpha.getTotal(), "**total 不反映关键字命中数**（厂商如此）");
    }

    @Test
    void keywordWithoutMatchYieldsNullItems() {
        listOf("alpha", "beta");

        var result = service().queryConfigMapList("ns", 1, 20, "zzz");

        assertNull(result.getItems(), "厂商：匹配为空 ⇒ items 置 null");
        assertEquals(2, result.getTotal());
    }

    @Test
    void keywordSpacesAreStripped() {
        listOf("my cm", "other");

        var result = service().queryConfigMapList("ns", 1, 20, "my cm");

        // "my cm" 去空格 ⇒ "mycm" ⇒ 无命中
        assertNull(result.getItems());
    }

    @Test
    void clusterFailureBecomesSystemException() {
        when(port.listConfigMapsWithoutIngressLb(anyString()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));

        var e = assertThrows(com.csm.paas.core.types.exception.SystemException.class,
                () -> service().queryConfigMapList("ns", 1, 20, null));
        assertEquals("configmap.get.list.failed", e.getI18nKey());
    }
}
