Type: research
Status: claimed
Blocked by: —

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
