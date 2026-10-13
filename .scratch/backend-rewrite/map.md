# Map: MSOP 后端 DDD 重写（可替换实现）

Label: `wayfinder:map`

## Destination

一个**可替换厂商 gaop-be 的重写后端**：JDK 21 + Spring Boot 3.3 + fabric8 7.x + MyBatis，按 `xfg-ddd-skills` 的六边形分层（Trigger → API → Case → Domain ← Infrastructure）。

判据：厂商前端**一行不改**即可跑通；`/api/gaop/v1/*` 契约与现有 DB schema（39 表 / 87 迁移）兼容；先以「软件-服务-端口」域端到端跑通并实测。

## Notes

- **领域**：机动平台微服务运行框架（MSOP，内部代号 gaop）的运行时管理后端——软件目录、服务与端口、应用与集群视图、战位与授权、用户与认证、审计/字典/设置、文件与日志。
- **开工前先读**：`docs/agents/domain.md`、`GLOSSARY.md`、`docs/adr/`。
- **使用的技能**：`xfg-ddd-skills`（六边形分层与模板）、`domain-modeling`、`tdd`、`code-review`、`grilling`、`research`。
- **长期偏好**：目标运行环境离线气隙 + 第一阶段只做 arm64；文档与术语用中文；结论标注证据等级（代码事实／运行实测／推断）与来源。
- **兼容优先**：契约与 schema 是硬边界，动它们必须单独开 ticket。
- **事实源**：`../recovered/gaop-be/`（厂商反编译源码）、`../recovered-web/`（前端复刻产物）、`../docs/MSOP-产品生命周期文档.md`、`../docs/findings/`。
- **验收工具**：`../recovered-web/api-catalogue.json`（196 个前端 service 函数）与厂商 `/v3/api-docs`（500 paths / 542 operations）是契约比对的两端。

## Decisions so far

<!-- 一条一行，指向承载细节的 ticket 或 ADR -->

- （地图前 grilling 第 1 轮）[目的地＝兼容替换](docs/adr/0001-compat-first-rewrite.md)：保接口与 schema，前端不改；不做重定接口的自研版。
- （地图前 grilling 第 1 轮）[技术底座](docs/adr/0002-jdk21-boot3-fabric8-mybatis.md)：JDK 21 + Boot 3.3 + fabric8 7 + 保留 MyBatis + HikariCP。
- （地图前 grilling 第 1 轮）[分层与工单载体](docs/adr/0003-hexagonal-layers-and-local-tracker.md)：xfg 六边形分层；`rewrite/` 独立仓库；本地 markdown tracker。

## Not yet specified

<!-- 看得见、但还说不成一句精确问题的雾 -->

- 限界上下文的切分与优先级（等 03 的证据才能问准）
- K8s 客户端替换后 `TenxService` 诸转换语义（具名端口的对象形状、`netWorkExport` 空桩、`system/lbgroup`）在重写中如何定义
- 灰度替换路径（新旧后端并存、Ingress/网关切流、回退）
- arm64 镜像与离线 Maven 私仓（不影响兼容性，但上生产必须）
- 前端 196 个函数中无人调用的端点是否保留（`/v1/app`、`service_entry` 等）
- 单节点 4.9GB 内存下的重写后端资源画像与限堆参数

## Out of scope

<!-- 只在目的地之外，不graduation；要做得另开一张地图 -->

- 厂商前端 gaop-web 的重写（已复刻为静态产物，保持不动）。
- 其它 gaop-* 辅助组件（gaop-auth / gaop-gateway-auth / gaop-fdfs / gaop-image-uploader / gaop-startup / gaop-diagnostic）的重写——除非某个域的可替换性必须连带改它们；那会是**另一张地图**。
- 厂商部署包与 Ansible 资产的修复（包本身尾部截断，需重新获取）。
