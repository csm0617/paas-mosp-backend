Type: research
Status: resolved
Blocked by: —

> 路径基准：本仓为 `/Users/csm/Desktop/PaaS/paas-core/`；厂商证据在 `/Users/csm/Desktop/Project/msop/`（`recovered/`、`recovered-web/`、`docs/`），下文相对路径均以该目录为基准。

## Question

为**限界上下文切分**提供证据（本 ticket 不做切分决定，只给证据）。对 39 张表 / 79 个 DAO / 41 个 service / 39 个 controller 做聚类，产出 `docs/findings/03-context-evidence.md`：

- 「表 → 拥有它的 DAO/service → 暴露它的端点」三方映射矩阵
- 候选上下文及其内聚/耦合度量（跨上下文引用计数：同方法里出现的多表访问、service 之间的注入关系）
- 每个候选上下文的规模（类数 / 端点数 / 表数），以及**是否含真正的不变量**（决定该域值不值得做充血聚合，而不是贫血 CRUD）
- 明确指出哪些 controller/service 是纯 CRUD 转发（无业务规则），哪些含真实规则

## 验收

- 矩阵可由脚本或可复现命令复查（把命令写进报告）。
- 每个候选上下文给出规模数字 + 至少一条反例讨论（"它看起来像 A 域，其实是 B 域"）。

## Answer

交付：`docs/findings/03-context-evidence.md`（1058 行，含附录 D）。

要点：39 表 / 40 逻辑 DAO / 41 service / 604 端点，其中**只有 216 个触库（35.8%）**，64.2% 是纯编排 →
切分要按"哪些端点真的碰数据"而不是按包名。**边界已被破坏**：8 个 controller 直连 Mapper，`PublicController` 一个类直触 29 张表、68 端点。
不变量最硬的三个域：用户/战位、授权许可、国际化。

**两条必改的坑**（附录 D）：① `deleted` 列有两套**相反**约定（`g_service` 有效值=1，`g_file*` 有效值=0）——
重写成布尔 `@TableLogic` 必然有一侧静默反向；② "单活跃 used=1" 有三处实现且**都没有事务边界**。

过程说明：本工单被并行派发过两次，两个版本都留档；较短版在 `docs/findings/_superseded/`，已交叉核对、结论无冲突。

## Answer
