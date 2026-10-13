# `PUT /v1/service/updateName` 的端到端真实验证——把上一轮那条"未验证"补掉

**证据等级**：〔运行实测〕（真实 K8s 集群 + 独立临时 namespace）。2026-10-14。
上一轮（`findings/48`）我明确标了"成功路径未做端到端验证"。本轮把它补上了。

## 1. 方法：独立临时 namespace，用完即删

```
kubectl create namespace paas-verify-ns
  造两个 Deployment：
    verify-with-ann  有注解 gaop/serviceLabel = "旧名字"
    verify-no-ann    **没有**该注解
后端以 GAOP_K8S_NAMESPACE=paas-verify-ns 启动，只指向这个临时 namespace
验证完：kubectl delete namespace paas-verify-ns（连带两个 Deployment 一起消失）
```

**不碰厂商环境里的任何工作负载**——这就是上一轮"不能拿厂商工作负载做实验"的解法。

## 2. 实测结果（两条路径都验到了）

| 路径 | 实测 |
| --- | --- |
| **成功**（有注解） | HTTP 200 `{"code":0,"data":true}`；注解 `旧名字` → **`新名字`**；i18n 两行都写入（`i18n.service.en_US/fr_FR.verify-with-ann`） |
| **失败**（无注解） | HTTP 500 `code=500` `msg="修改服务名称失败"`；**注解没有被补上**（仍为空）；**i18n 没有写入** |

## 3. 这条实测**证明**了上一轮那个修改是必要的

上一轮我把 fabric8 的 `edit()`（读-改-写）换成了真正的 JSON patch（`replace` 语义），理由是
"注解不存在时 `replace` 会失败，而 `edit()` 会把它补上并成功"——**当时这只是读码推断**。

**本轮实测把这个推断变成了事实**：
- 如果还是 `edit()`，`verify-no-ann` 那条会 **200 成功**、并把注解**补上**；
- 实测它返回 **500** 且注解保持为空 —— 即当前实现走的是 `replace` 语义，与厂商一致。

也就是说：**上一轮那条差异不是"洁癖式的一致性"，而是一条会被这个失败路径触发的真实行为差异。**

## 4. 清理（全部核验过）

```
namespace paas-verify-ns   → 已删除（NotFound）
两个 Deployment            → 随 namespace 一起消失
临时 NodePort svc           → 已删除（NotFound）
后端进程 / kubectl proxy    → 0
g_setting 里 i18n.service.* → 0 行
```

## 5. 仍然没做的事（如实延续）

- `PUT /v1/service/updateName` 的**StatefulSet 成功路径**没单独验（只验了"回退也失败→500"）；
  要验它需要一个**带注解的 StatefulSet**，属于下一轮几分钟的事；
- 厂商在该端点里还会写 i18n 自动名，我们落的是同一个键位（`g_setting['i18n.service.<lang>.<key>']`），
  实测写入成功、清理干净。

## 6. 状态

本轮**没有业务代码改动**（只做了验证与文档），全量用例仍 **34 类 220 条全绿**。
