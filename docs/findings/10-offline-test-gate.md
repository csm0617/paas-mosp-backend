# 离线门槛：不依赖集群的自动化测试（2026-10-14）

目标环境是气隙内网，所以"厂商前端一行不改"这条门槛的**自动化部分不能要求集群在场**。
本仓现在有 4 个测试类、7 条用例，`mvn test` 全绿（约 6 秒），全部离线。

| 测试类 | 锁住什么 | 对应门槛层 |
| --- | --- | --- |
| `NamedPortDeserializationTest`（infrastructure） | fabric8 7.9.0 能吃下 `targetPort: "http"` / `tcpSocket.port: "http"` | 前置（回归） |
| `ServiceRuntimePortTest`（infrastructure） | **workload 驱动**（Deployment+StatefulSet 全入列，没有同名 Service 也入列）与**具名端口保真** | 端口层行为 |
| `ServiceListAssemblerTest`（trigger） | 用**录制下来的厂商响应**回放：信封键/data 键/19 个项键逐字一致；`read-paths.json` 声明的每条前端读取路径成立；具名端口装配后不被吃掉 | L1 + L2 |
| `PaasCoreContextTest`（app） | Spring 上下文 + fabric8(mock) + **MyBatis 对 H2 真实往返**（建表、写入、读回） | 装配/持久化 |

## 怎么跑

```bash
mvn test                                   # 离线部分（全部 4 个类）
python3 tools/contract-check.py "GET /v1/service"   # 在线部分：需要集群 + 已启动的重写后端
```

## 它已经证明自己有用

抽出 `ServiceListAssembler` 时我漏了 `.deployment(...)` 一个字段：
**L1 形状测试照样通过**（19 个键仍在，只是值为 null），而 **L2 读取路径测试立刻失败**
（`data.services[].deployment.metadata.name` 解析为空）。这就是 L1 与 L2 的区别，也说明
"键齐全"不等于"契约没破"。

## 允许偏离是受控的

`read-paths.json` 里两条 `allowDeviation` 各有 `deviationKind`：

- `at-least-as-complete`（`mappingInfo`）：重写版必须非 null，允许比厂商更完整；
- `display-equivalent`（`restartCount`）：按前端 `a.restartCount || 0` 的语义，null 与 0 视为等价。

测试会断言**偏离集合与清单声明完全一致**——所以想新增一条偏离，必须显式改清单，不会被悄悄放宽。

## 离线覆盖不到的部分

真实集群返回的对象与厂商 client-java 输出之间的差异（显式 null、自造键 `proxy`、新 K8s 字段）只能在**在线对比**里看，
证据在 `06-prototype-diff.txt` 与 `runtime-samples/`。离线测试锁定的是"形状与读取路径"，不是"真实数据逐字相同"。
