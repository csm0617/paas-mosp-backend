# 服务切片：`PUT /v1/service/updateName` —— 错误路径对拉一致；patch 报文与回退顺序已钉住

**证据等级**：〔运行实测〕（错误路径对拉）+〔离线实测〕（报文/回退/i18n）+〔代码事实〕。2026-10-14。

## 1. 结果

| 项 | 实测 |
| --- | --- |
| 不存在的服务名 | 两侧都 **HTTP 500 `code=500` `msg="修改服务名称失败"`** —— 逐字一致 |
| 副作用 | 调用前后 `g_setting` 里 `i18n.service.*` 都是 **0 行**（两侧都没写） |
| K8s 对象 | 未创建/未修改任何对象（用的是不存在的服务名，patch 必然失败） |

## 2. 实现里最要紧的一处：**不能用 `edit()` 代替 JSON patch**

厂商发的是 `PATCH` + 报文
`[{"op":"replace","path":"/spec/template/metadata/annotations/gaop~1serviceLabel","value":"<name>"}]`。

我第一版用 fabric8 的 `edit()`（读-改-写）去设置注解——**语义不同**：
- 厂商的 `replace`：注解**不存在**时**失败** ⇒ 回退 StatefulSet ⇒ 若也失败则 500；
- `edit()`：会把缺失的注解**补上并成功** ⇒ 直接 200。

也就是说，一个**没有 `gaop/serviceLabel` 注解的 Deployment**，在厂商那里是"改不动"，
在我的第一版里却是"改成功了"。已改成真正的 JSON patch（`PatchContext.of(PatchType.JSON)`），
并把这条差异写进 gateway 的 javadoc。

这条是"**看代码以为等价、实际不等价**"的典型：`edit()` 和 `patch(JSON)` 都能改 YAML，
差别只在"目标路径不存在时"——而那正是厂商用 patch 而不是 update 的原因。

## 3. 报文与顺序都用离线测试钉住了

- `ServiceRuntimeGateway.serviceLabelPatchBody(name)` 的**逐字节**断言（含 `~1` 转义、`op` 必须是 `replace`、键顺序 op→path→value）；
- 编排三条：Deployment 成功即止（不碰 StatefulSet）；**Deployment 任意异常都回退 StatefulSet**；两者都失败 →
  `service.modify.name.failed` 且**不写 i18n**。

## 4. 如实标注：这条**没有**做端到端真实验证

它会真的改 K8s 工作负载。拿厂商环境的工作负载做实验违反本项目的清理纪律
（探针必须可完全回收，而改别人的 Deployment 注解不是"删掉探针行"能还原的）。
所以线上只对拉了**错误路径**（必然不写任何东西），成功路径的保证来自报文级断言 + 回退顺序断言。
**这条差距我标出来，不写成"已验证"。** 要做真实验证，应该在**独立的临时 namespace** 里
先造一个带该注解的 Deployment，再跑成功路径——列进下一轮。

## 5. 测试

新增 4 条（报文逐字节、Deployment 优先、回退 StatefulSet、双失败报错且不写 i18n）。
全量 `mvn test` **34 类 220 条全绿**（上一轮 216）。
临时 NodePort 与进程均已清理。
