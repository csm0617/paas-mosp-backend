package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.remote.adapter.port.ISshPort;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.NodeInfoVO;
import com.csm.paas.core.domain.software.service.impl.ServiceOpsServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 破坏性运维端点的离线契约（**类 C：本环境不可验证**，无节点可 SSH）。
 * <p>厂商出处：`ServiceController.schedulingNode:3087-3165`、`reStartBaseService:3313-3375`、
 * `deleteServicePods:2327-2343`；命令原文见 `utils/SshUtil.java:693-716`。
 */
class ServiceOpsTest {

    private final IServiceRuntimePort runtimePort = mock(IServiceRuntimePort.class);
    private final ISshPort sshPort = mock(ISshPort.class);

    private ServiceOpsServiceImpl service() {
        var impl = new ServiceOpsServiceImpl(runtimePort, sshPort);
        ReflectionTestUtils.setField(impl, "nodeRole", "control-plane");
        return impl;
    }

    @BeforeEach
    void stubReachableNode() {
        when(runtimePort.listNodes()).thenReturn(List.of(
                NodeInfoVO.builder().name("k8s-master")
                        .labels(Map.of("node-role.kubernetes.io/control-plane", ""))
                        .firstAddress("10.0.0.1").build(),
                NodeInfoVO.builder().name("worker01").labels(Map.of()).firstAddress("10.0.0.2").build()));
        when(sshPort.execOnHost(anyString(), anyString())).thenReturn("");
    }

    // ---------- PUT /v1/service/schedulingNode ----------

    /** 列节点失败 ⇒ **返回 false**（HTTP 200 + data:false，不报错）。 */
    @Test
    void schedulingNodeListFailureReturnsFalse() {
        when(runtimePort.listNodes()).thenThrow(new K8sApiException("boom"));

        assertFalse(service().schedulingNode("worker01", 0));
    }

    /** 节点名不在名单里 ⇒ 400 `service.node.name.not.found`（带 name）。 */
    @Test
    void schedulingNodeUnknownNameThrows() {
        var ex = assertThrows(WrongArgumentException.class, () -> service().schedulingNode("nope", 0));
        assertEquals("service.node.name.not.found", ex.getI18nKey());
        assertEquals("nope", ex.getArgs()[0]);
    }

    /** SSH 连不上 ⇒ 返回 false（不抛）。 */
    @Test
    void schedulingNodeSshUnreachableReturnsFalse() {
        when(sshPort.execOnHost(anyString(), anyString()))
                .thenThrow(new IllegalStateException("connect failed"));

        assertFalse(service().schedulingNode("worker01", 0));
    }

    /** `type=0` ⇒ `kubectl cordon <name>`，且连到 **master 的 IP**。 */
    @Test
    void schedulingNodeTypeZeroIssuesCordonOnMaster() {
        assertTrue(service().schedulingNode("worker01", 0));

        verify(sshPort).execOnHost("10.0.0.1", "kubectl cordon worker01");
    }

    /** `type=1` ⇒ `kubectl uncordon <name>`。 */
    @Test
    void schedulingNodeTypeOneIssuesUncordon() {
        assertTrue(service().schedulingNode("worker01", 1));

        verify(sshPort).execOnHost("10.0.0.1", "kubectl uncordon worker01");
    }

    /** 其它 type：两个 `if` 都不进（不发命令）但照样返回 true。 */
    @Test
    void schedulingNodeOtherTypeDoesNothingButReturnsTrue() {
        assertTrue(service().schedulingNode("worker01", 7));

        verify(sshPort, never()).execOnHost(anyString(), org.mockito.ArgumentMatchers.contains("kubectl"));
    }

    /** cordon 失败 ⇒ 400 `service.stop.schedule.failed`（带 name）。 */
    @Test
    void schedulingNodeCordonFailureThrowsStopKey() {
        when(sshPort.execOnHost(anyString(), org.mockito.ArgumentMatchers.contains("cordon")))
                .thenThrow(new IllegalStateException("kubectl failed"));

        var ex = assertThrows(WrongArgumentException.class, () -> service().schedulingNode("worker01", 0));
        assertEquals("service.stop.schedule.failed", ex.getI18nKey());
        assertEquals("worker01", ex.getArgs()[0]);
    }

    /** uncordon 失败 ⇒ 400 `service.start.schedule.failed`。 */
    @Test
    void schedulingNodeUncordonFailureThrowsStartKey() {
        when(sshPort.execOnHost(anyString(), org.mockito.ArgumentMatchers.contains("uncordon")))
                .thenThrow(new IllegalStateException("kubectl failed"));

        var ex = assertThrows(WrongArgumentException.class, () -> service().schedulingNode("worker01", 1));
        assertEquals("service.start.schedule.failed", ex.getI18nKey());
    }

    // ---------- POST /v1/service/reStartBaseService ----------

    /** 命令原文：`kubectl delete pod <podName> -n kube-system --force --grace-period=0`。 */
    @Test
    void restartBaseServiceIssuesExactDeleteCommand() {
        assertTrue(service().restartBaseService("kube-proxy-abc"));

        verify(sshPort).execOnHost("10.0.0.1",
                "kubectl delete pod kube-proxy-abc -n kube-system --force --grace-period=0");
    }

    /** 列节点失败 ⇒ false。 */
    @Test
    void restartBaseServiceListFailureReturnsFalse() {
        when(runtimePort.listNodes()).thenThrow(new K8sApiException("boom"));

        assertFalse(service().restartBaseService("pod"));
    }

    /** 命令失败 ⇒ 400 `ssh.delete.base.service.pod.failed`（带 podName）。 */
    @Test
    void restartBaseServiceCommandFailureThrows() {
        when(sshPort.execOnHost(anyString(), org.mockito.ArgumentMatchers.contains("delete pod")))
                .thenThrow(new IllegalStateException("kubectl failed"));

        var ex = assertThrows(WrongArgumentException.class, () -> service().restartBaseService("pod-1"));
        assertEquals("ssh.delete.base.service.pod.failed", ex.getI18nKey());
        assertEquals("pod-1", ex.getArgs()[0]);
    }

    // ---------- DELETE /v1/service/servicePods ----------

    /** `force=true` ⇒ 端口层收到 force=true（⇒ gracePeriodSeconds=0）。 */
    @Test
    void deletePodsPassesForceFlagThrough() {
        assertTrue(service().deletePods("gaop-namespace", List.of("p1", "p2"), true));

        verify(runtimePort).deletePod("gaop-namespace", "p1", true);
        verify(runtimePort).deletePod("gaop-namespace", "p2", true);
    }

    /** `force` 缺省 ⇒ 原样传 null（端口层据此不带宽限期字段）。 */
    @Test
    void deletePodsPassesNullForceThrough() {
        assertTrue(service().deletePods("ns", List.of("p1"), null));

        verify(runtimePort).deletePod("ns", "p1", null);
    }

    /** 任一删除失败 ⇒ 500 `service.delete.pod.failed`（带 podName）。 */
    @Test
    void deletePodsFailureThrowsSystemKey() {
        doThrow(new K8sApiException("boom")).when(runtimePort).deletePod("ns", "p1", true);

        var ex = assertThrows(SystemException.class, () -> service().deletePods("ns", List.of("p1"), true));
        assertEquals("service.delete.pod.failed", ex.getI18nKey());
        assertEquals("p1", ex.getArgs()[0]);
    }

    /** podNames 为 null ⇒ 直接 true（厂商会 NPE，我们主动收紧）。 */
    @Test
    void deletePodsNullListIsNoOp() {
        assertTrue(service().deletePods("ns", null, true));

        verify(runtimePort, never()).deletePod(anyString(), anyString(), anyBoolean());
    }
}
