# 分层采用 xfg-ddd-skills 六边形结构，决策与工单放本地 tracker

**Status: accepted**

重写代码按 `xfg-ddd-skills` 的六边形分层组织：`Trigger → API → Case → Domain ← Infrastructure`。Domain 只定义 Port/Repository 接口，实现在 Infrastructure 的 `adapter/` 下（`adapter/port/` 只调 `gateway/`，`adapter/repository/` 只调 `dao/` 与 `redis/`），**禁止 `persistent/` 包**。工程落在 `rewrite/` 独立仓库；决策地图与工单用本地 markdown tracker（`.scratch/backend-rewrite/`）。

## Considered Options

- **并入 `/Users/csm/Desktop/PaaS`**：被否——PaaS 是自研控制面，MSOP 是厂商运行时框架，两套领域语言混在一个仓库会互相污染。
- **用真 issue tracker（GitHub/GitLab）**：被否——目标环境离线，内网不可达。
- **在 `recovered/` 里原地改厂商代码**：被否——反编译源码是**证据**，不是基线；混淆证据与实现会让后续所有比对失效。

## Consequences

- Domain 层不允许直接依赖 DAO / Redis / gateway，跨域调用走 Case 层编排。
- 工单文件命名与阻塞边沿用 `.scratch/<effort>/issues/NN-<slug>.md` + `Blocked by:` 行；地图在 `.scratch/backend-rewrite/map.md`。
- `recovered/` 与 `recovered-web/` 保持只读证据身份。
