# `POST /v1/service/{serviceName}/node_port`：实现 + 独立 namespace 四种情形全通过（服务切片 14/33）

**证据等级**：〔运行实测〕（独立 namespace）+〔代码事实〕。2026-10-14。

## 1. 独立 namespace 实测（`paas-verify-np`，用完即删）

造两个 Deployment+Service：`np-svc`（容器声明 8080/9090）与 `np-noport`（容器不声明端口）。

| 用例 | 结果 |
| --- | --- |
| A：`type=NodePort`、无 `nodePorts` | 200 `data:true`；Service `type=NodePort`、ports 名 **`tcp-np-svc-0` / `tcp-np-svc-1`**（小写公式）、`port=8080/9090`、`targetPort=8080/9090`、**nodePort 由 K8s 自动分配**（30451/30330）✅ |
| B：容器**没有**声明端口 | 200 **`data:false`**（**静默路径**：不报错、也不 patch）✅ |
| C：Deployment 不存在 | 500 `设置NodePort失败：该服务no-such无法获取deployment信息`（消息里**带服务名**）✅ |
| D：显式 `nodePorts` | 200 `data:true`；`nodePort=30111/30112` 生效、`targetPort=port` ✅ |

**B 那条是关键**：它是"什么也没做"的路径，若只看成功用例根本不会发现——而厂商就是返回 `data:false`。

## 2. 实现（按 `findings/85` 的规格，一轮写完）

- gateway：`readDeploymentContainerPorts`（**null = Deployment 不存在 / 空列表 = 没声明端口**，两者语义分开）、
  `serviceExists`、`patchServicePorts`（整份 merge-patch `spec.ports` + `spec.type`）；
- port：把 K8s `ContainerPort`/`ServicePort` 与领域 VO 互转；
- 域服务：两个分支 + 默认 `ClusterIP` + 异常映射（422 & `provided port is already allocated` → 400
  `service.port.occupied`；其它 → 500 `service.update.nodeport.failed`）；
- 控制器：`POST /v1/service/{serviceName}/node_port`，返回 **布尔**。

## 3. 规格轮的价值（对照本会话的历史）

这一轮"实现一次过"，因为上一轮已经把 120 行读到可翻译：两个分支、三处易错点（名字公式小写、
两分支错误消息不同、空端口的静默 false）、异常映射，全都提前写在 `findings/85`。
**对照**：`gaop` 当年边读边写 → 连错两轮；影像域读错实现类 → 整版返工。
"先规格、后实现"在这个会话里已是第二次零返工（第一次是 `serviceAuth`）。

## 4. 状态

服务切片 **14/33**；测试 **41 类 278 条全绿**（上一轮 273）。
临时 namespace 已删（NotFound）、NodePort 已删、进程清零、工作树干净。
