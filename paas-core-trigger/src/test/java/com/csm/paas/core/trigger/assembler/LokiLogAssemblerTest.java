package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.HistoryLogDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loki `query_range` → 厂商 `HistoryLog` 的离线装配契约
 * （厂商 `TenxCloudImpl.getHistoryLog:159-235`）。
 *
 * <p>三级 `count` 的语义不同，是最容易写错的地方：ContainerLog = 日志**行数**、
 * PodLog = **容器个数**、HistoryLog = **pod 个数**。
 */
class LokiLogAssemblerTest {

    /** 正常响应：一个 pod 两个容器 ⇒ HistoryLog.count=1、PodLog.count=2、ContainerLog.count=行数。 */
    @Test
    void groupsByPodThenContainerAndCountsAtEachLevel() {
        String json = """
                {"status":"success","data":{"stats":{"summary":{"totalEntriesReturned":3}},
                 "result":[
                   {"stream":{"pod":"web-1","container":"web","filename":"/var/log/web.log"},
                    "values":[["1700000000000000000","hello"],["1700000000000000001","world"]]},
                   {"stream":{"pod":"web-1","container":"sidecar","filename":"/var/log/side.log"},
                    "values":[["1700000000000000002","side"]]}
                 ]}}""";

        HistoryLogDTO log = LokiLogAssembler.toHistoryLog(json);

        assertEquals(1, log.getCount(), "HistoryLog.count = pod 个数");
        assertEquals(1, log.getPodLogs().size());
        var pod = log.getPodLogs().get(0);
        assertEquals("web-1", pod.getPodName());
        assertEquals(2, pod.getCount(), "PodLog.count = 容器个数");
        assertEquals(2, pod.getContainerLogs().size());
        var container = pod.getContainerLogs().stream()
                .filter(c -> "web".equals(c.getContainer())).findFirst().orElseThrow();
        assertEquals(2, container.getCount(), "ContainerLog.count = 日志行数");
        assertEquals("/var/log/web.log", container.getFilename());
        assertEquals("1700000000000000000", container.getLogs().get(0).getTimeNano());
        assertEquals("hello", container.getLogs().get(0).getLog());
    }

    /** `totalEntriesReturned == 0` ⇒ `{podLogs:[],count:0}`（**不是 null**）。 */
    @Test
    void zeroEntriesYieldsEmptyShape() {
        String json = """
                {"status":"success","data":{"stats":{"summary":{"totalEntriesReturned":0}},"result":[]}}""";

        HistoryLogDTO log = LokiLogAssembler.toHistoryLog(json);

        assertEquals(0, log.getCount());
        assertTrue(log.getPodLogs().isEmpty());
    }

    /** 没有 `status` 字段 ⇒ 空日志。 */
    @Test
    void missingStatusYieldsEmptyShape() {
        HistoryLogDTO log = LokiLogAssembler.toHistoryLog("{\"data\":{}}");

        assertEquals(0, log.getCount());
        assertTrue(log.getPodLogs().isEmpty());
    }

    /** 原始 JSON 为 null / 空 / 坏 JSON ⇒ 空日志（不抛）。 */
    @Test
    void nullOrBrokenJsonYieldsEmptyShape() {
        assertTrue(LokiLogAssembler.toHistoryLog(null).getPodLogs().isEmpty());
        assertTrue(LokiLogAssembler.toHistoryLog("").getPodLogs().isEmpty());
        assertTrue(LokiLogAssembler.toHistoryLog("not-json").getPodLogs().isEmpty());
    }

    /** 同一个 (pod, container) 分片出现在多条 result 里时**合并**到同一个 ContainerLog（照抄厂商）。 */
    @Test
    void mergesRepeatedStreamEntries() {
        String json = """
                {"status":"success","data":{"stats":{"summary":{"totalEntriesReturned":2}},
                 "result":[
                   {"stream":{"pod":"p","container":"c","filename":"f"},
                    "values":[["1","a"]]},
                   {"stream":{"pod":"p","container":"c","filename":"f"},
                    "values":[["2","b"]]}
                 ]}}""";

        HistoryLogDTO log = LokiLogAssembler.toHistoryLog(json);

        assertEquals(1, log.getPodLogs().size());
        var container = log.getPodLogs().get(0).getContainerLogs().get(0);
        assertEquals(2, container.getCount());
        assertEquals(2, container.getLogs().size());
    }
}
