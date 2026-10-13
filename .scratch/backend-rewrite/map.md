# Map: paas-core 后端（MSOP 业务蓝本 · DDD 重写）

Label: `wayfinder:map`

## Destination

`paas-core` 产品仓里一个**以厂商 MSOP（gaop）业务为蓝本的可运行后端**：JDK 21 + Spring Boot 3.3 + fabric8 7.x + MyBatis，按 `xfg-ddd-skills` 的六边形分层（Trigger → API → Case → Domain ← Infrastructure）。

**第一条纵切的门槛（硬验收，非终态）**：厂商前端**一行不改**即可跑通 + 契约比对全绿 + 「软件-服务-端口」域在 172.16.209.25 e2e 实测。门槛过后允许在兼容层之外做产品化演进（重命名、加字段、拆分）。

## Notes

- **领域**：机动平台微服务运行框架（MSOP，内部代号 gaop）的运行时管理后端——软件目录、服务与端口、应用与集群视图、战位与授权、用户与认证、审计/字典/设置、文件与日志。
- **本仓**：`/Users/csm/Desktop/PaaS/paas-core`（坐标 `com.csm.paas.core:paas-core`）。
- **开工前先读**：`docs/agents/domain.md`、`GLOSSARY.md`、`docs/adr/`。
- **使用的技能**：`xfg-ddd-skills`（六边形分层与模板）、`domain-modeling`、`tdd`、`code-review`、`grilling`、`research`。
- **长期偏好**：目标运行环境离线气隙 + 第一阶段只做 arm64；文档与术语用中文；结论标注证据等级（代码事实／运行实测／推断）与来源。
- **厂商证据全是只读**，根目录 `/Users/csm/Desktop/Project/msop/`：
  - `/Users/csm/Desktop/Project/msop/recovered/gaop-be/`（反编译后端源码）
  - `/Users/csm/Desktop/Project/msop/recovered-web/`（前端复刻产物、`api-catalogue.json`、`regenerated/openapi-gaop-be.json`）
  - `/Users/csm/Desktop/Project/msop/docs/`（产品生命周期文档、GLOSSARY、findings）
- **验收工具**：`recovered-web/api-catalogue.json`（196 个前端 service 函数）与厂商 `/v3/api-docs`（500 paths / 542 operations）。

## Decisions so far

<!-- 一条一行，指向承载细节的 ticket 或 ADR -->

- （地图前 grilling 第 1 轮）[兼容是验收门槛，不是终态](docs/adr/0001-compat-first-rewrite.md)：第一条纵切必须证明厂商前端不改即可跑通；之后允许在兼容层外产品化。
- （地图前 grilling 第 1 轮）[技术底座](docs/adr/0002-jdk21-boot3-fabric8-mybatis.md)：JDK 21 + Boot 3.3 + fabric8 7 + 保留 MyBatis + HikariCP。
- （本轮）[分层与落点](docs/adr/0003-hexagonal-layers-and-local-tracker.md)：xfg 六边形分层；代码落 `/Users/csm/Desktop/PaaS/paas-core`，坐标 `com.csm.paas.core:paas-core`；决策与工单用本地 markdown tracker。

- （工单 01）[建立 paas-core 工程骨架](.scratch/backend-rewrite/issues/01-project-skeleton.md)：7 模块 + Boot 3.3.5 + fabric8 7.9.0 + MyBatis 3.0.3 + springdoc 2.6.0，`mvn package` 绿，依赖树里 `io.kubernetes` 命中 0。

- （先行验证）[具名端口在 fabric8 7.9.0 模型层可用](docs/findings/named-port-fabric8-7.9.0.md)：`targetPort: http` / `tcpSocket.port: http` 均可反序列化，2 个用例通过；真实集群数据仍待 06。

- （工单 05）[兼容硬边界与验收门槛](docs/adr/0004-frozen-contract-and-acceptance-gates.md)：锁 196 个前端端点 + 39 张表 + `/api/gaop/k8s/**`；厂商 87 个迁移冻结在 V0.8.6，新变更从 V0.9.0 起；验收四条门槛（第 4 条已完成）。

- （运行时取证）[厂商运行时契约样本](docs/findings/runtime-samples/README.md)：11 个端点实测（信封 `{msg,code,data}`、`/v1/service` 67 KB、K8s 代理数字为双精度）；并确认**实集群已修好两个白屏触发条件**，不能再当复现现场。

## Not yet specified

<!-- 看得见、但还说不成一句精确问题的雾 -->

- 限界上下文的切分与优先级（等 03 的证据才能问准）
- K8s 客户端替换后 `TenxService` 诸转换语义（具名端口的对象形状、`netWorkExport` 空桩、`system/lbgroup`）在重写中如何定义
- 门槛过后的产品化演进边界：哪些字段/表可以重命名、何时拆服务（现在只确定"允许"，没确定"怎么"）
- 灰度替换路径（新旧后端并存、Ingress/网关切流、回退）
- arm64 镜像与离线 Maven 私仓（不影响兼容性，但上生产必须）
- 前端 196 个函数中无人调用的端点是否保留（`/v1/app`、`service_entry` 等）
- 单节点 4.9GB 内存下的重写后端资源画像与限堆参数

## Out of scope

<!-- 只在目的地之外，不 graduation；要做得另开一张地图 -->

- 厂商前端 gaop-web 的重写（已复刻为静态产物，本轮只当验收工具）。
- 其它 gaop-* 辅助组件（gaop-auth / gaop-gateway-auth / gaop-fdfs / gaop-image-uploader / gaop-startup / gaop-diagnostic）的重写——除非某个域的可替换性必须连带改它们；那会是**另一张地图**。
- 厂商部署包与 Ansible 资产的修复（包本身尾部截断，需重新获取）。
- `../PaaS` 目录里既有的前端与历史文档的整理（不属于本仓）。
