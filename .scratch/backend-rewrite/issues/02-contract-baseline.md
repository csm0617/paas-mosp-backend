Type: research
Status: resolved
Blocked by: —

> 路径基准：本仓为 `/Users/csm/Desktop/PaaS/paas-core/`；厂商证据在 `/Users/csm/Desktop/Project/msop/`（`recovered/`、`recovered-web/`、`docs/`），下文相对路径均以该目录为基准。

## Question

把"接口兼容"变成**可机器比对**的基线。三方交叉：

1. `../recovered/gaop-be` 的 655 个映射注解（≈604 个 handler）
2. `../recovered-web/api-catalogue.json`（196 个前端 service 函数）
3. 厂商运行态的 `/v3/api-docs`（springdoc 1.5.2，500 paths / 542 operations / 930 schemas）

产出 `docs/findings/02-contract-baseline.json` + `02-contract-baseline.md`：

- 每个端点：method / path / 请求参数与 body 形状 / 响应形状（取自 api-docs schema 与 controller 签名）
- 三方对不上的清单：前端在用但 api-docs 没有的、后端有但无人调用的、路径近似但不同的
- 明确标出 K8s 代理路径（`/api/gaop/k8s/**`）——它们不是业务契约

## 验收

- JSON 里每个端点都能追到来源（文件+行号，或 api-docs path）。
- 差异清单给出计数，逐条给判定理由。

## Answer

已交付 `docs/findings/02-contract-baseline.{json,md}`（605 条端点 / 386 行）。关键：**前端 service 层是 258 个函数，不是 196**（catalogue 漏了 62 个）；542/542 api-docs operation 都能在源码找到 handler；259 个运行态端点无前端调用者；57 个 handler 是 `@ConditionalOnExpression("false")` 死代码。

## 进展（历史）

- `docs/findings/02-contract-baseline.json` 已落（1.0 MB）。
- `docs/findings/02-contract-baseline.md` **尚未产出** —— 本工单未完成，仍需：三方差异三桶计数 + K8s 代理路径单列。

## Answer
