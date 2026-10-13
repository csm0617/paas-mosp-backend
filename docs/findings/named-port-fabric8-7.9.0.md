# 具名端口的真正根因在厂商自建 DTO（2026-10-14 纠正）

> **纠正记录**：本文件初版把根因写成"厂商内嵌 `io.kubernetes:client-java:18.0.1` 的模型把 `targetPort`/`port` 声明成 Integer"。
> **这条是错的，已作废。** 复核（字节码 + 厂商源码）后确认：client-java 18.0.1 与 fabric8 7.9.0 的对应字段**都是 `IntOrString`**；
> 真正的 `Integer` 在厂商**自建**的 `third/k8s/IoK8sApiCoreV1*` DTO 里。

## 根因（逐行可查）

1. 厂商自建 DTO 把字段声明成 Integer：
   - `recovered/gaop-be/.../third/k8s/IoK8sApiCoreV1ServicePort.java:21` → `private Integer targetPort`
   - `.../IoK8sApiCoreV1TCPSocketAction.java:15` → `private Integer port`
   - `.../IoK8sApiCoreV1HTTPGetAction.java:22` → `private Integer port`
2. 转换走 **Gson→Jackson 往返**：`service/TenxService.java:496` `convertObj` = `JsonConfig.GSON.toJson(obj)` → `JsonConfig.JACKSON.readValue(json, IoK8sApiCoreV1Service.class)`。
3. `config/JsonConfig.java:38` 只给 `IntOrString` 注册了**序列化器**，没有反序列化器。
4. `TenxService.serviceResponse`（:506 起）`catch (Exception e) { log.error("转换类型失败", e); ... }` **吞掉异常**并把字段置空
   → 响应里的 `service` 为空 → 前端无条件读 `spec.ports` → **白屏**（不是 500，与现象一致）。

## 行动含义：换 fabric8 不会自动修好它

只要重写时继续沿用 `IoK8sApiCoreV1*` 这套 DTO（它同时就是给前端的 API 契约），故障会原样复现。修法是三件事一起做：

1. 三个字段的类型改成 `String` / `Object` / `IntOrString`；
2. 去掉 Gson→Jackson 往返（fabric8 原生就是 Jackson）；
3. 取消 `catch (Exception) → 置空` 的静默降级。

## 我方 fabric8 7.9.0 的验证（降级为"预防性回归"）

`NamedPortDeserializationTest`（`paas-core-infrastructure/src/test/...`）证明 fabric8 7.9.0 能正常反序列化
`ServicePort.targetPort: "http"` 与 `TCPSocketAction.port: "http"`，2/2 通过（JDK 21.0.7）。
这条结论本身成立，但它说明的是"**我们的新栈没有这个坑**"，**不是**"厂商的坑是 client-java 造成的"。
