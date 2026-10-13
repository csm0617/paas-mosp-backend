# 事实：fabric8 7.9.0 的模型能正常反序列化具名端口

**结论（运行实测，本机）**：fabric8 **7.9.0** 可正常处理

- `Service.spec.ports[].targetPort: "http"`（字符串目标端口）
- `Deployment.spec.template.spec.containers[].livenessProbe.tcpSocket.port: "http"`

两者都不抛异常，且 `IntOrString.getStrVal()` 均为 `"http"`。

## 为什么重要

厂商后端内嵌 `io.kubernetes:client-java:18.0.1` 的模型把这两个字段声明成 Integer，集群里只要出现具名端口就抛
`Cannot deserialize Integer from String` → `TenxService.convertObj` 把对象转成 null → `GET /api/gaop/v1/service` 报 NPE
→ **网络管理页白屏**。换到 fabric8 7.9.0 后，这个根因在模型层消失。

## 证据

- 用例：`paas-core-infrastructure/src/test/java/com/csm/paas/core/infrastructure/config/NamedPortDeserializationTest.java`
- 命令：`mvn -B -pl paas-core-infrastructure -am test -Dtest=NamedPortDeserializationTest -Dsurefire.failIfNoSpecifiedTests=false`
- 结果：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`（2026-10-14 02:27，JDK 21.0.7）
- 类路径实查（7.9.0 jar 内）：`io.fabric8.kubernetes.api.model.Service`、`io.fabric8.kubernetes.api.model.apps.Deployment`、`io.fabric8.kubernetes.client.utils.Serialization`
- 依赖证据：`docs/findings/01-deps.txt`（fabric8 全 7.9.0，`io.kubernetes` 命中 0）

## 边界（明确未验证）

这只证明**模型层**吃得下具名端口。真实集群返回的完整 Deployment/Service 对象是否还有别的不兼容字段
（例如厂商关心的 `system/lbgroup` 注解、`netWorkExport` 空桩语义），要等工单 06 用真实数据实测。
