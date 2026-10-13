# 分层采用 xfg-ddd-skills 六边形结构，代码落 paas-core

**Status: accepted**（2026-10-14 更新：代码落点由 `msop/rewrite` 改为 `/Users/csm/Desktop/PaaS/paas-core`）

重写代码按 `xfg-ddd-skills` 的六边形分层组织：`Trigger → API → Case → Domain ← Infrastructure`。Domain 只定义 Port/Repository 接口，实现在 Infrastructure 的 `adapter/` 下（`adapter/port/` 只调 `gateway/`，`adapter/repository/` 只调 `dao/` 与 `redis/`），**禁止 `persistent/` 包**。

代码落 `/Users/csm/Desktop/PaaS/paas-core` 独立 git 仓库，Maven 坐标 `com.csm.paas.core:paas-core`；决策地图与工单用本地 markdown tracker（`.scratch/backend-rewrite/`）。

## Considered Options

- **落在 `msop/rewrite`**：最初的选择；被用户改掉——msop 是厂商资产的分析工作区，产品代码应当落在产品仓里。
- **落 `/Users/csm/Desktop/PaaS` 根目录**：被否——根目录要留给产品文档与未来多个模块（前端、部署），代码放 `paas-core` 子目录。
- **用真 issue tracker（GitHub/GitLab）**：被否——目标环境离线，内网不可达。
- **在 `recovered/` 里原地改厂商代码**：被否——反编译源码是**证据**，不是基线；混淆证据与实现会让后续所有比对失效。

## Consequences

- Domain 层不允许直接依赖 DAO / Redis / gateway，跨域调用走 Case 层编排。
- 工单文件命名与阻塞边沿用 `.scratch/<effort>/issues/NN-<slug>.md` + `Blocked by:` 行；地图在 `.scratch/backend-rewrite/map.md`。
- 厂商证据目录（`/Users/csm/Desktop/Project/msop/recovered*`）保持只读。
