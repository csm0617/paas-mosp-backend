package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.HistoryLogDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loki `query_range` 响应 → 厂商 `HistoryLog` 形状的装配。
 *
 * <p>厂商出处：`TenxCloudImpl.getHistoryLog:159-235`（**JSON 解析在 trigger/infrastructure 做**，
 * domain 不引 Jackson）。
 *
 * <p>逐行照抄的三点：
 * <ol>
 *   <li>空判定：`!node.has("status") || data.stats.summary.totalEntriesReturned == 0`
 *       ⇒ `{podLogs: [], count: 0}`。**注意**：厂商用的是 `||` 且后面直接链式取值，
 *       所以"有 status 但没有 data.stats"时会 NPE ⇒ 500——我们按空日志处理（deviations B27）；</li>
 *   <li>聚合键是 `stream.pod` 与 `stream.container`（`filename` 也来自 `stream`）；</li>
 *   <li>`count` 三级不同：ContainerLog = 行数、PodLog = **容器个数**、HistoryLog = **pod 个数**。</li>
 * </ol>
 */
public final class LokiLogAssembler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LokiLogAssembler() {
    }

    /** @return 厂商 `HistoryLog`；`rawJson` 为 null / 解析失败 ⇒ 空日志（`{podLogs:[],count:0}`） */
    public static HistoryLogDTO toHistoryLog(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return empty();
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(rawJson);
        } catch (Exception e) {
            return empty();
        }
        if (node == null || !node.has("status")) {
            return empty();
        }
        JsonNode data = node.get("data");
        if (data == null) {
            return empty();
        }
        JsonNode summary = data.path("stats").path("summary").path("totalEntriesReturned");
        if (!summary.isMissingNode() && summary.asInt() == 0) {
            return empty();
        }
        JsonNode result = data.get("result");
        if (result == null || !result.isArray()) {
            return empty();
        }
        // podName → containerName → ContainerLogDTO（照抄厂商的 HashMap 两级聚合）
        Map<String, Map<String, HistoryLogDTO.ContainerLogDTO>> byPod = new LinkedHashMap<>();
        for (JsonNode entry : result) {
            JsonNode stream = entry.get("stream");
            if (stream == null) {
                continue;
            }
            String podName = stream.path("pod").asText();
            String container = stream.path("container").asText();
            String filename = stream.path("filename").asText();
            Map<String, HistoryLogDTO.ContainerLogDTO> containers =
                    byPod.computeIfAbsent(podName, k -> new LinkedHashMap<>());
            HistoryLogDTO.ContainerLogDTO containerLog = containers.get(container);
            if (containerLog == null) {
                containerLog = HistoryLogDTO.ContainerLogDTO.builder()
                        .container(container)
                        .filename(filename)
                        .logs(new ArrayList<>())
                        .count(0)
                        .build();
                containers.put(container, containerLog);
            }
            List<HistoryLogDTO.LogInfoDTO> logs = containerLog.getLogs();
            for (JsonNode value : entry.path("values")) {
                logs.add(HistoryLogDTO.LogInfoDTO.builder()
                        .timeNano(value.get(0).asText())
                        .log(value.get(1).asText())
                        .build());
            }
            containerLog.setCount(logs.size());
        }
        List<HistoryLogDTO.PodLogDTO> podLogs = new ArrayList<>();
        byPod.forEach((pod, containers) -> {
            List<HistoryLogDTO.ContainerLogDTO> containerLogs = new ArrayList<>(containers.values());
            podLogs.add(HistoryLogDTO.PodLogDTO.builder()
                    .podName(pod)
                    .containerLogs(containerLogs)
                    .count(containerLogs.size())
                    .build());
        });
        return HistoryLogDTO.builder().podLogs(podLogs).count(podLogs.size()).build();
    }

    private static HistoryLogDTO empty() {
        return HistoryLogDTO.builder().podLogs(new ArrayList<>()).count(0).build();
    }
}
