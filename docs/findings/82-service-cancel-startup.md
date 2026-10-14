# `POST /v1/service/cancelStartUp`：独立 namespace e2e 通过（服务切片 12/33）

**证据等级**：〔运行实测〕（独立 namespace + 错误路径对拉）+〔代码事实〕。2026-10-14。

## 1. 独立 namespace 实测（`paas-verify-su`，用完即删）

造一个 Deployment：**带注解 `gaop/serviceInitTime: "30"` + 一个 `startup` initContainer**。

| 用例 | 结果 |
| --- | --- |
| A：取消延时启动 | 200 `{"code":0,"data":true}`；调用后 **`annotations=[]`、`initContainers=[]`**（两者都被清掉）✅ |
| B：不存在的服务 | 400 `获取服务: no-such配置信息出现异常，无法正常取消延时启动时间` |
| B 对拉（厂商，用不存在的服务名，**不写任何东西**） | 400，**文案逐字相同**（只差服务名）✅ |

**清理**：`delete namespace paas-verify-su`（NotFound 确认）、临时 NodePort 已删、进程清零。

## 2. 照抄点（三处）

1. **`replaceNamespacedDeployment`（整份替换）而不是 patch** —— 与 `hostName`/`priority`/`patchCommand`
   那几个"merge patch"的端点不同。fabric8 里对应 `.replace(...)`；
2. **`initContainers` 置 null**（不是空列表）—— 效果上都是"没有 initContainer"，但报文不同；
3. **注解只在 `annotations != null` 时删**（厂商有这个判空；`annotations` 为 null 时跳过该步但**仍然**
   清 `initContainers` 并 replace）。

另外：**列表里有一个服务失败就中断**（厂商在循环里直接抛），不是"尽力而为"。

## 3. 状态

服务切片 **12/33**；测试 **41 类 270 条全绿**（上一轮 268）。
临时 namespace 已删（NotFound）、NodePort 已删、进程清零、工作树干净。
