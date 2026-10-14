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
- **验收工具**：前端 service 层 **259** 个函数（`recovered-web/api-catalogue.json` 只记了 196 个）与厂商 `/v3/api-docs`（500 paths / 542 operations）。

## Decisions so far

<!-- 一条一行，指向承载细节的 ticket 或 ADR -->

- （地图前 grilling 第 1 轮）[兼容是验收门槛，不是终态](docs/adr/0001-compat-first-rewrite.md)：第一条纵切必须证明厂商前端不改即可跑通；之后允许在兼容层外产品化。
- （地图前 grilling 第 1 轮）[技术底座](docs/adr/0002-jdk21-boot3-fabric8-mybatis.md)：JDK 21 + Boot 3.3 + fabric8 7 + 保留 MyBatis + HikariCP。
- （本轮）[分层与落点](docs/adr/0003-hexagonal-layers-and-local-tracker.md)：xfg 六边形分层；代码落 `/Users/csm/Desktop/PaaS/paas-core`，坐标 `com.csm.paas.core:paas-core`；决策与工单用本地 markdown tracker。

- （工单 01）[建立 paas-core 工程骨架](.scratch/backend-rewrite/issues/01-project-skeleton.md)：7 模块 + Boot 3.3.5 + fabric8 7.9.0 + MyBatis 3.0.3 + springdoc 2.6.0，`mvn package` 绿，依赖树里 `io.kubernetes` 命中 0。

- （先行验证）[具名端口在 fabric8 7.9.0 模型层可用](docs/findings/named-port-fabric8-7.9.0.md)：`targetPort: http` / `tcpSocket.port: http` 均可反序列化，2 个用例通过；真实集群数据仍待 06。

- （工单 05）[兼容硬边界与验收门槛](docs/adr/0004-frozen-contract-and-acceptance-gates.md)：锁 259 个前端函数命中的端点 + 39 张表 + `/api/gaop/k8s/**`；厂商 87 个迁移冻结在 V0.8.6，新变更从 V0.9.0 起；验收四条门槛（第 4 条已完成）。

- （**工单 07 落定**）[切片顺序与灰度替换路径](docs/adr/0005-slice-order-and-cutover.md)：顺序 = 软件-服务-端口(✅) → 认证(✅) → **用户/战位(本轮)** → 授权许可 → 国际化 → 审计/字典/设置 → 集群/应用视图 → 文件；原则「**入口先做、防腐层后做**」。并存 = 双 Deployment（服务名 `paas-core`）+ Higress **按路径前缀**切流，回退=改回路由（秒级，不重新部署/不回滚库）；门禁阶段两套后端**共库共 Redis**。域"完成"四条：契约 L1+L2 全绿 + 该域 e2e + 既有用例不退化 + 不留越层包。
- （**工单 08 落定**）[契约判定：语义等价](docs/adr/0006-contract-semantic-equivalence.md)：L1/L2 不绿=缺陷；L3 记录在案；追认两条偏离（`restartCount` display-equivalent、`mappingInfo` at-least-as-complete 且反向不允许）；**新增第四条规则——L3 偏离一旦被证明前端可见即升级为缺陷**；`proxy` 键仍未定论，不得标成永久豁免。

- （运行时取证）[厂商运行时契约样本](docs/findings/runtime-samples/README.md)：11 个端点实测（信封 `{msg,code,data}`、`/v1/service` 67 KB、K8s 代理数字为双精度）；并确认**实集群已修好两个白屏触发条件**，不能再当复现现场。

- （重定阻塞）工单 06 的阻塞由 `01, 04` 改为无：01（骨架）已完成；04 的依赖迁移表只对**全量迁移**必要，只读纵切不需要。契约比对基线改用已抓取的运行时样本（`docs/findings/runtime-samples/`）。04 落地后仍会回补引用。

- （工单 06）[纵向切片跑通](docs/findings/06-prototype-result.md)：真实集群 200/73.7KB，信封与 19 个单项键与厂商一致；发现 4 处差异（workload 驱动、`runningDeployment` 未定论、null 序列化、缺认证）。
- （工单 04）[迁移障碍逐条定论](docs/findings/04-migration-blockers.md)：**纠正了白屏根因**（不是 client-java，是厂商自建 `IoK8sApiCoreV1*` DTO 的 Integer + Gson→Jackson 往返 + 吞异常）。
- （新开）[契约判定标准](.scratch/backend-rewrite/issues/08-contract-comparison-criterion.md)：语义等价 vs 逐字节等价，待你拍板。

- （工单 02）[契约基线](docs/findings/02-contract-baseline.md)：605 端点全量对账；**口径更正——前端 service 层是 259 个函数不是 196**；542/542 api-docs 都被源码覆盖；**263** 个运行态端点无前端调用者（后端有但无人调用共 321）。
- （事实）[前端实际读取的字段](docs/findings/09-frontend-read-paths.md)：19 个键里 **7 个前端从不读**（含 `runningDeployment`/`fixedName`/`imageInfo`）；`serviceAuth` 为 null 会让页面显示"认证失败"，必须填。

- （新开）[认证最小闭环](.scratch/backend-rewrite/issues/10-auth-minimum-loop.md)：门槛前置（登录 + `gateway-token` cookie），阻塞于 07（要先定"自带还是继续用 gateway-auth"）。

- （工具）[契约比对器](tools/contract-check.py) + [读取路径清单](docs/findings/read-paths.json)：L1 形状 / L2 前端读取路径 / L3 已知偏离；`GET /v1/service` 目前 L1+L2 全过（含 2 条待追认的允许偏离）。
- （证据）[厂商样本库](docs/findings/runtime-samples/manifest.json)：26 个只读端点实测响应，供后续各域做基线。

- （门槛自动化）[离线测试门槛](docs/findings/10-offline-test-gate.md)：4 个测试类 7 条用例、`mvn test` 全绿且不需要集群；L1/L2 由回放厂商样本锁定，允许偏离按 `deviationKind` 受控。

- （事实·已纠正）[中文乱码的真正来源](docs/findings/11-own-seed-double-encoding.md)：库里就是双重编码的（hex 实证），厂商与我方字节一致——是我方播种时没带 `--default-character-set=utf8mb4`；**先前的"厂商缺陷"结论已作废**。

- （事实）[认证最小闭环契约](docs/findings/12-auth-contract.md)：UUID token + `gateway-token:<uuid>`=`user:role` + `login-list:<user>:<role>` + cookie；两侧 `geneKey` 一致、同一 Redis；实测 TTL 86400s。工单 10 的实现已无障碍（"自带 vs 网关"由 ADR-0001 推导，待用户确认）。

- （工单 10）[认证最小闭环已实现并验证](docs/findings/12-auth-contract.md)：登录/cookie/Redis 会话/401 语义全部照厂商；**厂商 gaop-be 认我方 cookie（200）**，互操作成立。

- （事实·重要）[前端首次 e2e 实测](docs/findings/13-frontend-e2e-first-run.md)：真实前端**渲染成功**、`/v1/user` 被真实消费（200）；但 `/software/*` 因 `access: canAdmin` 判不出而 403（cookie 不足以建立战位/权限态）。
- （新发现）**页面外壳端点清单**：`/settings`、`/v1/lang/current`、`/v1/mode/current`、`/v1/user/get`、`/v1/licence/*`、`/ws/info` —— 不属于软件-服务-端口域，却是任何页面能启动的前置。工具：`tools/frontend-e2e-harness.py` + `tools/frontend-e2e-check.js`。

- （事实·重要）[厂商鉴权在网关侧](docs/findings/14-vendor-auth-is-gateway-side.md)：`/v1/role`、`/v1/service`、`/settings`、`/v1/user/get` **无 cookie 也是 200**，只有 `/v1/user` 是应用级 401。**第 8 轮我加的 requireSession 属于过度收紧，本轮已撤回。**
- （进展）外壳端点 `/settings`、`/v1/mode/current`、`/v1/lang/current`、`/v1/user/get` 已实现且与厂商样本一致；登录页已能拉到账号/语言数据，**卡在 `/v1/licence/info` 404**（页面停在"正在获取授权信息"，未定论）。

- （进展）[授权端点 + 登录页打通](docs/findings/15-licence-and-login-page.md)：`/v1/licence`、`/v1/licence/info`（试用期与厂商同值）、`/ws/info` 已实现；**登录页的账号/门户下拉已由我们的后端供数**，前端自发起 `/v1/role?username=admin`。仅剩"自动填口令"这一步的工具问题。
- （未实现·低优先）`/v1/licence/checkAndActiveGis`、SockJS 真实传输端点。

- （进展·重要）[真实前端已把我们的后端当后端用](docs/findings/16-frontend-drives-our-backend.md)：外壳全部端点 200（`/v1/user`、`/v1/user/get`、`/v1/role`、`/settings`、`/v1/lang/current`、`/v1/licence/info`、`/v1/mode/current`）。
- （待解）`/software/workloads` 仍被路由守卫弹回登录页；可疑 404：`/v1/licence/checkAndActiveGis`、`/v1/pub/quickStart`（**未定论**，先补这两个再复测）。

- （里程碑）[前端不再被弹回登录页](docs/findings/17-user-endpoint-returns-userlogined.md)：根因是 `GET /v1/user` 必须是 **20 键的 `UserLogined`**（含 `role`，前端用它判 `canAdmin`）——我此前误记为 14 键（打印截断所致）。修正后真实前端停在 `/gaop/software/workloads` 并渲染完整外壳。
- （下一轮）该页还要三个端点：`/v1/image/projectList`、`/v1/role/software`（均已有样本）、`/v1/pub/etcd_label/check`（需抓）。

- （进展·重要）[STOMP over SockJS 通道打通](docs/findings/18-stomp-channel.md)：前端 job/工作负载/容器页的数据来自 `/topic/k8s/proxy/{Service,Deployment,Pod}` 推送；已用 domain 端口 + fabric8 watch + `withSockJS()` 实现，**日志证明 STOMP CONNECTED(1)、watch 在跑且已发消息**，前端控制台打印 `socket 连接成功`。
- （待补）工作负载表仍 0 行，剩三个 404：`/v1/image/projectList`、`/v1/role/software`（有样本）、`/v1/pub/etcd_label/check`（需抓）。

- （A/B 对照·重要）[我们的后端与厂商渲染等价](docs/findings/19-three-endpoints-and-ab-comparison.md)：同一份前端分别指向两个后端，`/software/workloads` 的渲染结果**完全一样**（都无内容区、都弹引导、STOMP 都连上、API 都 200）。"内容区为空"**不是我们的实现造成的**；原因未定论（疑似本地 harness 不支持 WS 升级）。
- （已补）`/v1/role/software`、`/v1/pub/etcd_label/check`、`/v1/image/projectList`（Harbor 域，如实返回空）。

- （A/B·最终对照）[支持 WS 升级后 A/B 仍一致](docs/findings/20-ab-parity-with-websocket.md)：真实 WebSocket 升级 + 端点全 200 + 会话有效的前提下，我们与厂商**渲染与行为完全一致**（含"内容区为空"）。新工具 `tools/frontend-e2e-harness.mjs`（Node 零依赖，支持 WS 透传）。
- （契约差异·已修）**`max-login` 默认 1（单点登录）**：厂商 `application.yml:125` 默认 1，超出会 DEL 最早会话；我们此前传 0（不限制）。已改为默认 1 并加单测断言。

- （**里程碑**）[首个真实数据页渲染成功](docs/findings/21-first-rendered-data-page.md)：厂商原样前端 + 我们的后端 → `/software/application/service` 渲染出 **9 行服务表**（名称/状态/运行副本均来自真实集群，数据源是 `200 /v1/service`）。门槛三条（前端不改、契约比对、e2e）均已具备证据；截图 `docs/findings/artifacts/2026-10-14-service-page.png`。
- （部分修）`/v1/service` 的 `imageInfo` 已按厂商格式填上；但页面「镜像」列**不是读 `imageInfo`**（产物里 0 处该字面量），仍显示 `undefined`——待定位真正的 dataIndex。

- （交接）[阶段交接文档](docs/HANDOFF-2026-10-14.md)：门槛三条均达成的证据、已实现清单、偏离与未定论、待决策项（07/08）、复现与注意事项。
- （查清）页面「镜像」列的 `undefined/undefined` 是**前端怪癖**（`image.split("/")` 取 parts[1]/[2]，要求三段镜像名），厂商自己的数据同样如此；我方镜像串与厂商逐字一致。

- （**切片 3 开工**）[工单 11](.scratch/backend-rewrite/issues/11-slice3-user-role.md)：K1 共 **70** 端点（含 `AuthController` 7 条，03 号口径把它归 K3），**前端有调用者 49**；本切片只做有调用者的部分，分 3a 用户 / 3b 战位 / 3c 子角色，每片按「读路径→写路径→批量」推进。
- （**切片 3a 完成**）[用户管理页 e2e 成功](docs/findings/26-e2e-user-page.md)：厂商原样前端 + 我们的后端 → `/gaop/usermanage/account` 渲染出 10 列表头 + 1 行真实数据；**`GET /v1/user/info` 与厂商在线响应逐字节相等**；A/B 对照标题/表头/数据行/导航全一致。新增 `GET /v1/user/info`（domain `UserQueryServiceImpl` → infra 三个 DAO → trigger 装配器）。
- （**错误契约**）[400/401/500/-1 与 404 不套信封](docs/findings/24-error-contract.md)：厂商 `msg` 是**本地化文案**（键解析失败回退成键），故引入厂商消息包作契约数据；**Spring 6 的未映射路径会抛 `ErrorResponse`，必须显式还原 Spring 默认 404**，否则 404 会变成 500/-1。
- （**更正 03 号证据**）[实库唯一约束](docs/findings/23-k1-live-schema-and-unique-constraints.md)：`g_user.username`、`g_role.key`、`g_sub_role.key` **有 DB 唯一索引**（V0.0.1 用小写内联 `unique`，被大写 grep 漏掉）——"只靠应用层判重"的结论作废；并发下的行为与建模都要按"领域规则 + DB 兜底"来。
- （事实）[K1 实现级证据档案](docs/findings/22-k1-identity-position-evidence.md)（968 行）：70 端点逐条入参/返回、7 张表终态列、33 条不变量（标注 controller/service/DB）、~80 个 i18n key；并指出 `GET /v1/user` 厂商**原样返回 BCrypt 口令哈希**（我们置 null，属**刻意**的安全向偏离，已入 `read-paths.json` 的 L3 账）。
- （**下一步**）`/usermanage/account` 唯一 404 是 `GET /v1/sub_role/switchRole`——**子角色下拉是这一页的前置**，所以切片 3c 不是可延后的边角。

- （**切片 3c 完成**）[子角色列表端点](docs/findings/27-e2e-sub-role-endpoint.md)：`/gaop/usermanage/account` **零 404**；A/B 标题/表头/数据行/导航全一致。为拿到非空形状，向**厂商**后端建了一个探针子角色、抓完样本后删除（已确认回到空列表）。
  - 三条照抄细节：列表接口 `userList`/`roleList` 恒为数组（新增接口却是 `null`）；`nameFr`/`nameUs` 在列表里恒为 null；关键字非空时**厂商漏了 `startPage`**（`total = list.size()`，不分页）。
  - **新记一条"差异不是缺口"**：A/B 的 `/api` 集合差异可能只是 **SockJS 传输形态**（xhr_streaming vs WS 升级），两侧都 200/204——下次别误判成实现缺口。

- （**切片 3b 完成**）[席位管理页 e2e 成功](docs/findings/28-e2e-role-page.md)：厂商原样前端 + 我们的后端 → `/gaop/usermanage/role` 渲染 9 列表头 + 1 行真实数据；**5 个端点与厂商逐字节相等**（switchRole type1/type2、list、role、{id}）。
  - 本轮真正的难点是**四个读端点的集合填充差异**（同一 16 键模型）：`/v1/role` 只填 softwareList；`/list` 多填 userList；`/switchRole` 四个全填（`onLineUserList` 恒为数组）；`/{id}` 四个全 null 但 `nameFr`/`nameUs` 兜底成 name。两个反直觉处已写进证据。
  - 运行证据锁住了两处"最容易做错"：页面「关联应用」显示的正是**硬编码伪门户条目**；「已登录用户 = 1」证明 `onLineUserList` 来自 **Redis `login-list*` 扫描**。
  - （未定论）厂商 `getSubRoleListByRoleId` 用 `HashSet`，多元素时顺序不确定 → 多子角色时无法逐字节对齐。
  - （遗留缺口）`/usermanage/role` 还会调 **`GET /v1/software`**（我们 404）——属 **K2 软件目录**，即战位页的"关联应用"对话框需要 K2。

- （**切片 3 写路径开工**）[删战位的 6 表级联](docs/findings/29-role-delete-cascade.md)：`DELETE /v1/role?roleId=a,b,c` 成功与两条失败路径**均与厂商逐字节相同**（`{"msg":"OK","code":0,"data":null}` / "找不到该席位" / "无法删除该席位"）。
  - **`paas-core-case` 从空壳变成真正的一层**：厂商的 `@Transactional` 打在含 `forEach` 的整个方法上，所以"删两个、第二个是内置战位"必须**整体回滚**——事务边界下沉到 DAO 就会漏提交。为此引入 `IRoleDeleteCase` 并给 case 模块加 `spring-tx`。
  - **对真实库验证过级联**（不是只跑 H2）：建探针战位 → 关系表插行 → 用我们的后端删 → 5 张表全为 0；`g_sub_role_role` 的插入因外键失败，**正好证明该外键存在**。探针已清理，库恢复为 2 个战位。
  - 照抄不修好：厂商保留字有 4 个，但删除路径**只护 `admin`/`dev`**（`cicd`/`root` 实际可删）——已写进单测锁住。
  - （待回补）厂商删战位还会删 i18n 自动名（本环境 no-op），等切片 5。

- （**切片 3a 写路径**）[删用户：权限矩阵 + 假成功 + 级联](docs/findings/30-user-delete-and-error-parity.md)：四条路径（401 / 删 admin / 删不存在 / 批量不存在）与厂商**逐字节相同**；真实库级联验证（探针用户 + 两张关系表 → 全为 0）。
  - **必须照抄的"假成功"**：既非 `admin` 用户、也无 admin 战位的调用者，厂商返回 **200 + `data:true` 但什么都不删**——不是 403。
  - **两处只有实测能发现的坑**：① 不存在用户的文案是 `user.id.not.found`（"查无此id的用户"），不是我按源码读出来的 `user.not.found`（那句在控制器路径上是死代码）；② **405 必须走 `-1` 信封**——厂商 500/"系统错误"，我们初版给了 405。修正后 404/405 四条路径对拉全等。
  - （厂商缺陷，不复制）厂商 `POST /v1/user` 缺 `roleListKey` 会 NPE（500）**但用户行已插入**，留下半成品用户；重写版下一轮按空列表处理并放进事务。

- （**切片 3a 写路径·新增用户**）[POST /v1/user 与 8 小时时区坑](docs/findings/31-user-add-and-birth-timezone.md)：四条错误路径与厂商**逐字节相同**（含**非管理员 → HTTP 500 不是 403**）；成功响应除 `id`/时间戳/`password`（刻意置 null）外**逐字段相同**。
  - **本轮最有价值的发现**：`birth` 曾差 8 小时。两层原因都要修——① 厂商 Jackson 配了 `time-zone: UTC` + `date-format: ...SSS'Z'`（`'Z'` 是**字面量**）；② **驱动版本差异**：厂商 Connector/J **5.1.48** 把 DATE 读成"JVM 本地零点"，我们 **8.3.0** 读成"UTC 零点"。
  - **修法是显式建模而不是猜**：领域层 `birth` 用 **`LocalDate`**（`date` 列本来就该是日期），进出 JSON 走 `DateTimes` 按系统时区换算；JDBC URL 也照抄厂商的 `serverTimezone=UTC`。修完两侧都是 `2000-01-01T16:00:00.000Z`。
  - 照抄不修好：厂商的用户名"非法字符"检查是**死代码**（`matches()` 配字符类 → 只有单字符才命中，而长度校验已拒掉长度 1）→ `a@b#c` 能通过，已用单测锁住。
  - （有意偏离）厂商 `add` 无事务、缺 `roleListKey` 会留半成品用户；我们整个用例一个事务、缺字段按空处理。

- （**切片 3a 读路径·用户详情**）[GET /v1/user/detailByUserId](docs/findings/32-user-detail-endpoint.md)：与厂商**整体 JSON 相等**（17 键、含嵌套 Role 对象）；不存在用户 → 400 `user.missing.user`（"找不到该用户"，不是 `user.not.found`）。
  - 本轮的看点仍是**同名不同形状**：`/v1/user/info` 的 `roles` 是**字符串数组**、`belongRole` 叫 `belongUsers`；而详情端点的 `roles`/`belongRole` 是 **Role 对象**、另有 `admin` 布尔——两个端点**不能共用 DTO**。
  - 小重构：把 admin/dev 伪门户覆盖 + 三个布尔提成 `RolePortalSupport` 共用，避免两处各写一遍后分叉；同时保留 `/v1/role/{id}` 不覆盖的差异。
  - （待办）页面级"点编辑开抽屉"的交互链路还没跑过真实前端；`PUT /v1/user/{userId}` 下一轮（规则已读齐，含"厂商强制写回 email/birth/phone ⇒ 改不动"这条）。

- （**切片 3a 写路径·修改用户**）[PUT /v1/user/{userId} 与会话 TTL 缺陷](docs/findings/33-user-update-and-session-ttl.md)：四条错误路径**逐字节相同**（普通用户改别人 → **HTTP 500**）；成功路径除 `id`/时间戳/`password`/`username`/`uuid` 外逐字段相同。
  - **"哪些字段能改"已在真实库核对**：厂商把 **email/birth/phone 强制写回原值**（改不动），`desc` 空白置**空串**（新增时是 `null`）——照抄并分别用单测锁住。
  - **一条实测出来的会话缺陷，决定不复制**：厂商 PUT 会把调用者会话 TTL 从 86400 **砍到 600 秒**（Redis TTL 实测：改别人也砍、改自己不带 username 也砍）。这是"改个昵称 10 分钟后被踢下线"的破坏性副作用，按"厂商缺陷不自动成为契约"记录不实现；**代价**是切流期间打到厂商后端仍会被砍，演练时留意。
  - 关系语义与新增**相反**：`PUT` 里三个列表为空即**删除**该类关联（新增是跳过）。
  - （清理教训）探针改过的**既有行**也要按字节恢复——`admin` 的 `nickname`/`desc` 是双重编码字节，已逐字节核对恢复。

- （**切片 3a 补齐三个端点**）[按主键查用户 + 两个战位配置端点](docs/findings/34-user-by-id-and-role-relation.md)：`GET /v1/user/{id}` **整体 JSON 相等**；`userToRole`/`{id}/roles` 的响应与**库内关系条数**两侧一致。
  - **四个"找不到用户"文案各不相同**（全部实测）：删用户 `user.id.not.found` / 查用户 `user.id.query.not.found` / `{id}/roles` 用 `user.not.found` / `userToRole` 用 `user.missing.user.info`——**任何"统一成一个键"的整理都会破坏其中三条**。
  - 两个名字很像的端点语义不同：`userToRole` 替换**全部**关联且**不校验** roleId 存在性、`HashSet` 去重、返回 `true`；`{id}/roles` 只替换 `type=1`、**逐个校验存在性**、不去重、返回 `null`。已分别实现并写进单测。
  - 测试 112 → **118 条**全绿。K1 的"用户"一侧读+写基本闭环；战位一侧还差新增/修改（`POST /v1/role`、`PUT /v1/role/{id}`）。

- （**切片 3b 写路径**）[新增/修改战位 + 两处对拉发现的差异](docs/findings/35-role-write-and-two-parity-finds.md)：`POST /v1/role` 与 `PUT /v1/role/{id}` 响应**逐字段相同**，四条错误路径一致。
  - **我自己引入的一处偏离（已修）**：新建用户的 `updatedAt` 被设成了 now，厂商是 **null**（`add` 只设 `createdAt`）。上轮对拉时我把两个时间戳一起放进"忽略集合"，顺手把"该字段是否该存在"也忽略了——**教训：'值必然不同'可以忽略，'该字段是否该有值'不能忽略**。
  - **一处厂商缺陷（不复制）**：`PUT /v1/role/{id}` 的响应里 `name` 恒为 **null**——`RoleController.java:249` 把参数位置传错了（签名 `(module,language,key)`，却传了 `(module,key,name)`），`getAutoNameMessage` 必然返回 null 并覆盖掉已取出的名字。只影响响应，库里和随后的 GET 都正常。
  - 保留字矩阵照抄：**只要表单带 `key` 就不许改内置战位**（哪怕 key 与当前相同）；不带 key 则可以只改名字；`cicd`/`root` 不受保护；新增时 key 上界写的是 `<0`（空 key 能过）。
  - **对拉方法教训**："同一探针两侧顺序执行"会污染对照——唯一约束类用例两侧必须用互不相同的值。
  - （未复现的异常）改完域代码后有一次 `K1UserInfoContractTest` 7 条全 404，随后连续两次全量 + 单独跑均全绿；如实记录。
  - 测试 118 → **124 条**全绿。`POST /v1/role` 的 `softwareList`（软件名列表）需 `g_software`，**属 K2 欠账**。

- （**切片 3 收尾**）[exist_role_name + {roleId}/users + K2 规模](docs/findings/36-k1-tail-and-k2-software-scope.md)：两个端点对拉一致（`exist_role_name` 三个名字两侧都 `false`——库里是双重编码中文，**厂商也查不到 `true`**；`{roleId}/users` 键名是 **`userList`** 且 `total` 含被列表过滤掉的 `root`）。
  - **过程教训**：本轮用 Python 脚本一次改 5 个文件，其中一条 `replace` **静默没生效**却照样打印 "patched"，编译也通过（没有编译期引用）——是**测试失败**才暴露的。改完必须**逐文件 grep 校验**，且对"新增映射"这类无编译期引用的改动要有**运行期断言**。
  - **K2 `/v1/software` 的真实规模（重要）**：它不是"只读列表"，而是**带 K8s 富化的聚合端点**——每个软件行要额外算 `releaseStatus`/`web`（0/2/按 Deployment）/`url`（CS 类型拼 `gaop://cs?...`）/`roleList`，加上 22 列与两级排序（**`order_top` 恒降序在前**）。**因此放弃"先做一个只返回 id/name 的简化版"**：那会让前端拿到缺字段的数据，宁可晚一轮做完整。
  - **下一轮开工清单**：22 列 + 4 个附加键；`releaseStatus`/`web` 的推导需**各建探针软件行**（本环境 `g_software` 为 **0 行**，现有样本只锁住空分支）；探针两侧用不同 key/name。

- （**K2 开口**）[GET /v1/software](docs/findings/37-k2-software-list.md)：**整体 JSON 相等**，且覆盖了每个分支——3 行探针软件（抓完即删）撑开了 `url`（CS/WEB）、`releaseStatus`（0/1）、`web`（**0/2/1 三种都实测到了**）、`roleList`（null/非空）、两级排序（`order_top DESC` 恒在前）。
  - K8s 依赖走端口隔离：`IWebEntryStatusPort` ← `WebEntryStatusPort`（复用切片 1 的 `ServiceRuntimeGateway`）；本页所有 `service_name` **一次**查完 Deployment（厂商也是先取一次全量）。
  - 照抄厂商的可疑逻辑：`setServiceStatusBySoftware` 内层先设 3 又**无条件**设 1（那个 3 永远被覆盖）；厂商 `status==null` 会 NPE，我们按"状态缺失"给出 2（记录为收紧）。
  - 排序列做了**白名单化**（厂商直接把 `order` 映射到动态 SQL 列）；`read-paths.json` 新增 15 条读取路径（证据：`p__software` 的 dataIndex + 字面量计数）。
  - 测试 126 → **137 条**全绿。K2 剩下的：软件详情/新增/发布（白名单）、CS 上传、导入导出、`g_software_app` 映射。

- （**K2 续**）[POST /v1/software 新增软件](docs/findings/38-k2-software-create.md)：**23 键，除 id/key/name/routerKey/时间戳外逐字段相同**；三条错误路径逐字节相同。
  - **对拉抓出我自己臆造的三个字段**：`appName`（厂商表单没这个字段）、`version`（同）、以及**响应键数 27 vs 23**（POST 返回 `Software`，列表返回 `SoftwareMessage`）。根因是我**从数据库列反推入参**——`g_software` 有 `app_name`/`version` 列，但新增表单根本不接受它们。**教训：入参字段要读厂商的表单类，不能读表结构。**
  - 同类问题因此新增独立的 `SoftwareDTO`（23 键），不再复用列表项 DTO——与用户/战位的"同名不同形状"是同一类坑。
  - 照抄：`routerKey` 被重写成 `/<key>/`（表单值不生效）、保留字**只挡 `api`**、key 首字符四条规则、关联已存在则跳过、`order` = 该战位已有软件数+1。
  - 主动收紧：厂商对 `roleListKey` 不判 null 会 NPE；我们按空列表处理。
  - 测试 137 → **144 条**全绿。K2 剩下：软件详情/修改/删除、发布（白名单）、CS 上传、导入导出、`g_software_app` 映射、`/auth` 授权列表。

- （**K2 闭环一半**）[POST /v1/software/release 放行/禁止](docs/findings/39-k2-software-release.md)：四种情形（type=1 放行、type=0 有行/无行、空列表）**全部与厂商一致**——`releaseStatus` 的来源既能读（列表）也能写（放行）。
  - 照抄一处**不对称**：`type=1` 逐个处理完才返回；**`type=0` 一旦删到就立刻返回 true、删不到立刻 false**（只看第一个存在的软件，后面的 id 不处理）。
  - **未实现的副作用**：厂商最后会新起线程 `GET http://<gateway>/refresh` 通知网关重载白名单（fire-and-forget，不影响响应）。我们没接管网关 → 记入待办，与 ADR-0005 的切流演练绑在一起。
  - **本轮刻意没做 `GET /v1/software/{id}`**：读了实现后确认它是**三个域的聚合**（`SoftwareInfo` 21+ 键里 `licence` 要 K3、`uploadStoreRecord` 要上传记录、`status`/`serviceList` 要 K8s 现场态）。在缺 K3 的前提下做它只能返回一堆 null 冒充数据——**留着不做**，先理清 `licence` 与上传记录的最小只读形态。
  - 测试 144 → **148 条**全绿。

- （**K2 续**）[DELETE /v1/software/{id}](docs/findings/40-k2-software-delete.md)：成功响应 `data:null` 与不存在（400 "id指定软件不存在"）两侧一致；四表按厂商顺序删除（`g_role_software`(按 **app_id**) → `g_software_app` → `g_service_port` → `g_software`），库内实测清零。
  - 两处声明：**收紧**——厂商这条链路没有事务（会半删），我们包进一个事务；**未实现**——删 `g_service_port` 有行时厂商会通知网关（同 findings/39 的待办）。
  - **一处未端到端验证**：`g_software_app` 在本环境是空表、探针插不进去 ⇒ 那条 delete 没真正执行过；形状与其他三条相同，但如实标为"未端到端验证"。
  - 测试 148 → **152 条**全绿。
- （**K2：软件详情**）[GET /v1/software/{id}](docs/findings/41-k2-software-detail-and-machine-code-gap.md)：**23/23 键一致，只差 1 个字段**（含 screenShotList 拆分、roleKey/NameList、status 默认 "STOP"、非 BS 时 serviceList 为 null、version=上传记录 id）。
  - 那个唯一差异是 `licence.code` 的机器码部分——**我们一直是占位符 `MSOP-REWRITE-PLACEHOLDER`**（`LicenceRepository` 的 `@Value` 默认值）。厂商 = `Base64(gaopCode + "," + "gaop")`；`platformId` 是硬编码 `"gaop"`，`/data/licenceInfo/machine_codes.json` 在厂商 pod 里**不存在** ⇒ 走配置值。
  - **更正旧记录**：早期把 `/v1/licence` 记为"已验证"其实只是**形状**对（`{code,existGist}`）；`code` 的**值一直是错的**。教训：只验形状不验值会埋雷，本轮被详情端点暴露。
  - ~~未定论（做 K3 前必须定）~~ → **已定论（本轮）**：机器码**稳定且可复刻**。原始机器码 = 配置项 `machine.code`（本集群 `420DF971-09EC-BA4A-BE74-B4868EB29A53`）；平台 id 是硬编码 `"gaop"`；两条拼法**只差一个尾逗号**（`/v1/licence` 带、软件详情不带）。已实现并**逐字节对拉一致**。
  - **我上一轮那个「机器码不稳定」的结论作废**：`BE87` 是我**从终端手抄 base64** 时把 `NzQ` 看成 `ODc` 所致；程序化取数后连续 6 次/3 次都稳定。**教训：不透明值必须程序化提取，绝不手抄**——手抄会把"我没看清"伪装成"厂商不稳定"，而这种假结论最容易让人放弃对齐。
  - **同一个坑第二次踩**：`selectRelationRoles` 用 `Map` 取列，H2 列标签大写 → `[null]`（与 `selectIdAndKeyByKeys` 同源）。已改用 PO；结论：不确定大小写的库取多列一律用 PO。
  - 测试 152 → **155 条**全绿。

- （**K2 收尾：修改**）[PUT /v1/software/{id}](docs/findings/43-k2-software-edit.md)：23 键 + 空白 desc（落 null）+ 表单缺字段被置 null + 存在性错误，全部与厂商一致。
  - 三处照抄的古怪：`BeanUtils.copyProperties` 连 null 一起拷（缺字段 → null）；desc 空白**在新增落空串、在修改落 null**（同一个 helper 的返回值一个被覆盖一个被丢弃）；**name 重复与 key 重复用的是同一个 i18n 键**。
  - **我第一版把注释写对、代码写错**（注释说"空白 → null"，代码写的是 `.desc(form.getDesc())` 给空串），是对拉照出来的。教训：注释写对不等于代码写对；凡"读代码得到的结论"必须有一条实测或断言钉住。
  - 工具层：**同一个坑第三次踩**——python heredoc 里嵌三引号导致解析失败、整段补丁静默不执行，还被前面的 `print("patched")` 掩盖，靠编译错误才发现。改用 edit 工具 + 每步 `grep -c` 验证（当场查出 4 个补丁全没落地）。
  - 测试 157 → **162 条**全绿。**软件域 CRUD + 发布态闭环**：列表 / 新增 / 详情 / 修改 / 删除 / 放行，六条全部对拉一致。

- （**K3 开口**）[激活码校验](docs/findings/44-k3-activation-verify.md)：验签（RSA512 + issuer `gaop`）+ claim 解析 + **机器码交集** + 过期判定 + 异常映射，全部实现。
  - **关键手段**：厂商私钥拿不到 ⇒ 测试里**自己生成一对 RSA 密钥**，把公钥塞进仓储、用配对私钥签出同构 JWT，让真实实现去验。**不用 mock 假装验签通过**；也因此把上一轮"active_code 分支不可达"的问题变成了"可达且已验证"。
  - 两处**容易写成一样**的异常键已分别钉住：平台版 `licence.invalid.key.signature` vs 软件版 `licence.invalid.signature`。
  - 实测事实：**JWT 的 exp 只到秒**（end 被截断）；**表名必须查真库**（我猜成 `g_auth_info`，真库是 **`g_auth`**）；`g_auth` 无 `default_auth` 行时厂商 `IndexOutOfBounds`，我们返回 null 折算成未激活（记录为收紧）。
  - 软件详情的 `licence` 不再是硬编码 expired，改成真的走 `getSoftwareLicenceInfo` 两条路径。
  - K3 仍缺：平台激活写入端点、GIS 在线授权、`/v1/licence/info` 的已激活分支。
- （**K3 写入路径勘查**）[勘查与清单](docs/findings/45-k3-write-path-recon.md)：本轮**零业务代码改动**，把 `POST /v1/licence` / `DELETE /v1/licence` 的语义与 `/v1/licence/info` 的三个分支勘查清楚，留下可直接开工的清单。
  - **排除一个疑似缺陷**：`getLicenceInfo` 用的是第三个机器码变体 `getMachineCodeBase64WithoutGisInfo`，实测其值与 `/v1/licence` **相同**（都带尾逗号）⇒ 我们现在的 `/v1/licence/info` 是对的。限定：本环境无 GIS，有 GIS 时未验证。
  - 已激活分支（`authStatus=true`）仍是空白，要等 `POST /v1/licence` 写出来才第一次可达。
- （**K3 写入路径**）[POST/DELETE /v1/licence + 已激活分支](docs/findings/46-k3-activate-write-path.md)：**已激活分支第一次可达**（`authStatus = !(now > endTime)`）。
  - 对拉：无效激活码的错误路径**逐字节一致**（连那条脏消息 `The string 'n�.' doesn't have a valid JSON format.` 都一模一样——因为把厂商那串 catch 链原样照搬了）；且确认**无副作用**（调用前后 `system.active.code` 都是 0 行）。
  - `activate` 用的是**服务端自己的机器码**（测试第一次失败就是因为我拿别的机器码去签，必然无交集）。
  - **清洁教训**：扫库发现第 30 轮留下的两行 `g_setting`（`i18n.software.<lang>.probeaddv`）——厂商 `saveAutoNameMessage` 的副作用，我当时只清了 `g_software`/`g_role_software`。**清洁要按"被调端点的写入路径"倒推要清哪些表**，不是只清刚看到的那几张。已清掉并做全库 `LIKE '%probe%'` 复扫。
  - 未实现：TenX/GIS 侧效应、授权文件、时速云时间早于授权工具那一支（本环境因 `merged` 为 null 而**不可达**）。
  - 测试 210 → **214 条**全绿。
- （**服务切片开工**）[GET /v1/service/labels + 服务域清单](docs/findings/47-service-slice-labels-and-inventory.md)：对拉一致（7 个 label 键，键集合与值集合全同）。
  - **唯一不能用字节对拉的端点**：厂商用 `HashMap` 聚合再 `forEach`，数组顺序天然不稳定 ⇒ 契约只能定在"键集合 + 每个键的值集合"。把这个比较方式写进文档，避免以后有人拿 diff 判它失败。
  - 两个易读反的字段：`name` 是 **label 的键**（不是节点名）；`labels` 是该键的**去重值**（不是标签串）。
  - **服务域清单**（厂商 30+ 端点，我们原只有 1 个）：分三类——只读+K8s 现场态 / 写 K8s（有真实副作用）/ 改 K8s annotation 上的业务元数据。下一轮建议：第一类挑 `GET /v1/service/{serviceName}`（可纯离线钉形状），第三类挑 `/updateName`（可在隔离 namespace 真验证）。
  - 测试 214 → **216 条**全绿。
- （**服务切片**）[PUT /v1/service/updateName](docs/findings/48-service-update-name.md)：错误路径对拉**逐字一致**（500 / `code=500` / "修改服务名称失败"），且无副作用（两侧都没写 i18n、没动 K8s）。
  - **实现里最要紧的一处**：厂商发的是 JSON patch 的 `replace`，**注解不存在时失败** ⇒ 回退 StatefulSet ⇒ 再失败才 500。我第一版用 fabric8 `edit()`（读-改-写）会把缺失注解**补上并成功**——一个没注解的 Deployment 在厂商那里"改不动"、在我这里"改成功"。已改成真 JSON patch。典型的"看代码以为等价、实际不等价"。
  - 报文逐字节断言（`~1` 转义、`op=replace`、键顺序）+ 回退顺序 + 双失败不写 i18n，共 4 条离线测试。
  - **如实标注**：成功路径**没做**端到端验证（会真改工作负载，不能完全回收）；下一轮应在**独立临时 namespace** 里造带注解的 Deployment 再做。
  - 测试 216 → **220 条**全绿。
- （**补掉上一轮的验证缺口**）[updateName 端到端实测](docs/findings/49-service-update-name-e2e.md)：在**独立临时 namespace**（`paas-verify-ns`，用完即删）造两个 Deployment 做验证——不碰厂商任何工作负载。
  - 成功路径：HTTP 200 `data:true`，注解 `旧名字`→`新名字`，i18n 两行都写；失败路径（**无注解**）：HTTP 500 "修改服务名称失败"，**注解没被补上**、i18n 没写。
  - **这条实测把上一轮那个修改从"读码推断"变成"事实"**：若仍是 `edit()`，无注解的那条会 200 并补上注解；实测是 500 且注解为空 ⇒ 当前实现的 `replace` 语义与厂商一致。**上一轮那条差异不是洁癖，是会在这个失败路径上被触发的真实行为差异。**
  - 清理全部核验：namespace/deployments/NodePort 均 NotFound、进程 0、i18n 0 行。
  - 仍没验：StatefulSet 的**成功**路径（需要一个带注解的 StatefulSet），下一轮补。
- （**服务详情的坑先探明**）[GET /v1/service/{name} 在本环境返回不出成功响应](docs/findings/50-service-detail-unobservable.md)：列表里前 8 个服务逐个打详情，**8/8 全 400 "服务X不存在"**。
  - 根因：存在性由 `tenxCloud.existService → tenxService.existService`（TenX 层，本环境无真实对端）判定 ⇒ 恒 false。
  - **结论：现在做它是错的**——拿不到成功响应就没有外部证据，而"注释写对了代码写错了"我已经栽过两次。**先解决"怎么验证"再动手**：下轮第一件事是查 `existService` 到底看什么，能造数据恢复对拉就恢复；不能则按 list 形状实现并**明确标注未经对拉**。
  - **从前端拿到服务域真实全貌：共 33 个端点**（比从 controller 数更准，前端会调的才是契约面），已完成 3 个，剩 30 个。
- （**服务详情勘查终局**）[厂商该端点在参考环境自身 500](docs/findings/51-service-detail-vendor-defect.md)：两个缺陷叠加。
  - 第一道门：`existService` 按 **label selector `system/svcName=<name>`** 查，而本环境所有工作负载只有 `app=<name>` ⇒ 恒 false（8/8 全 400 的根因）。**验证方式**：临时给 `gaop-diagnostic` 加该 label（只动 metadata.labels、不触发滚动），门就过了——随后**字节级还原**并经核对。
  - 第二道门（终局）：过门后厂商返回 500/-1，trace 指向它自己：`TenxService.responseContainer:604` 的 `getLimits().get("cpu").getNumber()` —— **只判了 limits 非 null、没判键存在**，本环境工作负载没有完整 limits ⇒ NPE。**是厂商缺陷，不是数据缺失。**
  - 结论：这个端点**无法通过任何手段观测到成功响应**。下一轮取舍建议：**先做服务域里可对拉的端点（3），再照代码实现详情并注明"证据来自列表端点 `/v1/service` 的 `ServicesItem` 形状（已对拉），详情端点本身厂商恒 500"（1）**。
  - 用例总数更正
- （**scale 端点**）[findings/66](docs/findings/66-service-scale-endpoint.md)：实现 + 独立 namespace（`paas-verify-sc`）e2e——`1→3` 成功且集群实际 replicas=3，**响应是整份 Deployment 对象**（不是布尔）。
  - **一处刻意保留的偏离**：失败时厂商 500/code=500/`msg` **空**，我们 500/code=500/`msg`=fabric8 原始消息。决定保留消息（清空只会丢诊断信息），**但记在案，不写成"完全一致"**。
  - **三个写端点边界行为两两不同**（给"不要按上一个套下一个"用）：`hostName` 回退 StatefulSet + 两边空白静默 no-op + 返回布尔；`priority` **不回退** + 空白设空串 + 返回布尔；`scale` 回退 StatefulSet + 都失败 500 + **返回整份对象**。
  - 服务切片 **8/33**（3 个写端点均有独立 namespace e2e）；全量 **236 条**全绿。
- （**priority 端点**）[findings/65](docs/findings/65-service-priority-endpoint.md)：实现 + 独立 namespace（`paas-verify-pr`，用完即删）e2e——`high-priority` 真改上、空白 level 变**空串**、不存在→**400 且与厂商文案逐字相同**。
  - **与 hostName 的两处不同**（最容易按上一个套下一个）：`hostName` 读不到 Deployment **回退 StatefulSet**，`priority` **不回退**直接 400；`hostName` 两边空白是**静默 no-op**，`priority` 空白 level 是**主动设空串**。
  - **错误契约一次对上**：这轮键名是从代码抄的（上轮是凭经验猜的，被对拉纠正）。**能从代码抄就别猜；必须猜的一定要对拉。**
  - 服务切片 **7/33**；测试 233 → **236 条**全绿。
- （**hostName 端点**）[findings/64](docs/findings/64-service-hostname-endpoint.md)：**实现 + 独立 namespace（`paas-verify-hn`，用完即删）端到端验证**——两边空白→200 true 且不改（静默 no-op）；设 `myhost`→真改；`old-host`→`new-host`；不存在→400。
  - **关键修正**：厂商只在"**读不到** Deployment"时回退 StatefulSet，不是"hostname 为空"时。我第一版把两者混成一个返回值（null 既表示不存在又表示空），被用例 A 照出（预期 200 实为 500）；端口拆成 `deploymentExists` + `deploymentHostname`。同族：`edit()`vs`patch`、`fixedName`vs`gaop/serviceLabel`——**两个语义接近的东西被合并，差异只在边界暴露**。
  - **错误契约我猜错了**：先写 500/`...host.name`，对拉实测是 **400**/`...host.pod`，已改。**错误契约也必须对拉。**
  - 服务切片 **6/33**；测试 229 → **233 条**全绿。
- （**serviceInfoByNode 勘查**）[findings/62](docs/findings/62-service-info-by-node.md)：`/v1/service` 是 9 条、它是 **0 条** ⇒ **不是同一份数据**（上一轮"同形⇒可复用"只对了形状）。算法已读完（masterNodeName → 按 `(nodeName|keyword|masterNodeName)` 缓存 → `getServicesItems` → order 1..6 排序 → 分页）；**关键未读**：`getServicesItems` 的节点归属规则（"为什么是 0"的答案）。
  - 定位：**可观测但参考退化**（同 `baseComponent`）。按 `gaop` 的经验，**只有能解释"为什么是 0"，这个 0 才算证据** ⇒ 下一轮先读 `getServicesItems`，能解释 0 再实现。
  - **第 79 轮已读全 `getServicesItems` 并更正解释**：真因**不是** master 节点（那是第 55 轮的推断），而是过滤器要求 **`gaop/serviceLabel` 注解非空**，而本环境**没有任何工作负载带该注解**（实测只有 `restartedAt`）⇒ 恒空。`serviceNamed` 来自 **Pod 的容器名**（`gaop-redis` 的容器名是 `redis`）。
  - 该端点因此可拿"可解释的 0"对拉；**但要先读完 `ServicesItem` 的装配**（podInfoList/belongNodeList/mappingInfo）——那部分仍未读。
  - **服务域工作量判断更新**：能拿到**非空对拉**的存量基本用完（已对拉 5 个）。往后只剩三类：退化参考+能解释的 0 / 独立 namespace 里的写验证 / 按代码实现+标注证据来源。**这个判断要在下一轮先确认，别再逐个端点试。**
- （**方法感知分诊 + 3 个新可观测读端点**）[findings/61](docs/findings/61-service-triage-method-aware.md)：新发现 `serviceInfoByNode`（**与 `/v1/service` 同形，下一轮首选**）、`otherPlugin`（`{daemonSetList,deploymentList,statefulSetList}`）、`restartInfo`（本环境空）。剩余端点性质已归纳：已对拉 5 / 可观测读待做 3 / 依赖 Harbor 1 / 内容退化 1 / 厂商自身坏 3 / 破坏性写若干 / 非破坏性写（需独立 namespace）。
- （**服务域分诊**）[10 个端点逐个探测，只有 4 个能拿 200](docs/findings/52-service-endpoint-observability-triage.md)：可对拉=`gaop`/`serviceAuth`（**与我们已对拉的 `/v1/service` 同形**）+ `imageStorage`/`baseComponent`（各有前置）。
  - ⚠️ **本文 §2 的"400（参数类）"已被 [findings/61](docs/findings/61-service-triage-method-aware.md) 修正**：`hostName`/`priority`/`node` 根本不是 GET（是 `PUT`/`POST`/`DELETE`；`node` 是**删节点**、`servicePods` 是**删 Pod**，破坏性写）。**分诊要先看 HTTP 方法再看返回码。**
  - `hostName`/`priority`/`node` → 400（参数类）；`servicePods` → 400（与 `existService` 同源，见 findings/51）；`baseEvent`/`event` → **500 厂商内部异常**。
  - **本轮零业务代码改动，理由写清了**：`gaop`/`serviceAuth` 差"过滤/排序规则"（不读码会形状对内容错）；`imageStorage` 的值来自 Harbor 而我们**没有 Harbor 客户端**，返回 0/0 属于"用 0 冒充数据"，拒绝；`baseComponent` 要先读全形状与那组 DaemonSet 名。
  - 下一轮确定顺序：`gaop` → `serviceAuth` → `baseComponent` →（`imageStorage` 等 Harbor 域）。
- （**gaop 端点开工资料齐了**）[算法与常量全量摘录](docs/findings/53-gaop-service-list-algorithm.md)：下一轮**无需再读源码**。
  - 摘全了：`gaopName` 18 条显示名映射、`PASS_FRAME_SERVER_NAME_SET`(9)/`BASE_SERVER_NAME_SET`(7)、`AppTypeLabelsEnum`、过滤算法（含 `system/appType` 标签优先）、keyword 两段式、伪服务 gis-server/gis-jb（本环境因 existService 恒 false 而**不可达**）。
  - **顺带发现厂商缺陷**：没有 `appType` 标签时，`type==1` 与 `type==2` **用的是同一套 PASS_FRAME 集合**（`serviceInBaseService`/`BASE_SERVER_NAME_SET` 成了死代码）⇒ 本环境查 `type=2`「基础服务」实际返回**框架服务**。照抄，并加注释与用例钉住。
  - **本轮已落成代码**：`GaopServiceNames`（18 显示名 + 两名单 + 标签常量）+ `GaopServiceFilter.matches(...)`，5 条测试。其中一条把缺陷钉成**可执行证据**（遍历基础名单断言 type=2+无标签时全部查不出、同时断言名单本身正常）——将来谁"顺手修好"它，测试会立刻红。
  - **待对拉确认**：厂商 `fixedName` 取不到时是 **null**，我实现成回退原名；接控制器时要拿一个不在名单里的服务对拉确认。
  - **端点已接通但未收敛**（[findings/54](docs/findings/54-gaop-endpoint-divergence.md)）：vendor `total=0/[]`（两个 type 都是），mine 6 个。
    - **问题 A（阻断对拉）**：厂商数据源是 `tenxCloud.allPageService(null)`，与 `/v1/service` 不是同一条链路；怀疑其 items 的 `deployment` 为 null 被第一步丢弃 ⇒ 该端点在本环境**结构性地返回空**。**未确认**（还没读 `allPageService`）——**在确认之前这条端点的对拉无效**。
    - **问题 B（我们的 bug，下一轮先修）**：`fixedName` 实测是服务名而不是中文显示名（`GaopServiceNames.fixedName` 单测是对的 ⇒ 问题在装配链路上）。
    - 已落代码：`appType`/`fixedName` 字段 + 端口取 `system/appType` 标签 + `queryGaopServiceList`（过滤/固定名/keyword 两段式/分页）+ 控制器（含 keyword>45 → 400）。装配器改为"有 fixedName 用它、否则回退服务名"，**保持已对拉的 `/v1/service` 行为不变**。
    - 另标注一处**近似**：厂商兜底 `filterServiceName` 未读全，暂按"名字或显示名忽略大小写包含"实现，注释已写明未经对拉。
  - **两个问题都定论**（[findings/55](docs/findings/55-gaop-rootcause-and-known-fix.md)）：
    - **问题 B 已修并验证**：根因是上一轮那个补丁脚本**断言失败中途退出**，装配器那处从未改（半成品状态却像成功）。修复后 `gaop` 返回中文显示名（框架授权/框架后端/网关/…），**并回归确认 `/v1/service` 的 fixedName 仍是服务名**（已对拉行为一字未变）。
    - ~~问题 A 已定论~~ → **该结论已被 findings/57 否掉**（同一数据源在 `serviceAuth` 返回 9 条）；真因**仍未定论**。错法：从正确前提推出错误结论，漏了"该方法可能被覆盖"这一步验证。可复用自查：**凡"X 所以 Y 必然为 Z"的推论，若 Y 是关键结论，必须先用一次独立观测证实**。
    - **问题 A 原（已否）结论**：`allPageService` 是 `@RequestLine("GET /api/v2/clusters/{id}/services")` —— **对 TenX 云 API 的远程调用**；本环境无该外部服务 ⇒ 恒空。**与 `/v1/service` 本质不同**（后者直读 K8s）。
    - **处置**：保留我们的 K8s 直读实现 + 记录成因。**不为了"本环境看起来一致"去模仿返回空列表**——那等于把可用功能改成空壳以迎合坏参考。真实部署里 TenX 存在时两者的集合应当相同。
    - 仍欠：厂商 keyword 兜底 `filterServiceName` 未读全，当前是"名字/显示名忽略大小写包含"的**近似**（注释已标）。
  - **keyword 兜底规则已精确实现**（[findings/56](docs/findings/56-filter-service-name-exact.md)）：读全 `filterServiceName:2070-2081` 后确认我上一版的近似**三条全错**——它匹配的是注解 **`gaop/serviceLabel`**（用户改过的显示名）而不是 `fixedName`（平台内置显示名），第二段**只看注解不看名字**，且**大小写敏感**。
    - 改动：`ServiceRuntimeEntity.serviceLabel` + 端口取 `spec.template.metadata.annotations["gaop/serviceLabel"]` + `filterServiceName` 精确实现；3 条用例，其中"没有注解的服务永不命中兜底"正是我上一版会答错的那题。
    - 教训：**标注"这是近似"不等于核对过了**——近似值本身可能就是错的。
  - **真正根因找到并对拉首次全绿**（[findings/58](docs/findings/58-gaop-real-rootcause.md)）：过滤 lambda 的**最后一行** `return isGaopApp && result`（`isGaopApp = labels["system/appName"] == "gaop"`）—— **参考环境没有这个标签 ⇒ 9 条全部被正确过滤掉**。与 TenX 无关（57 已否），也不是 type 名单问题。
    - 修复后 **type=1/type=2 两侧都是 total=0**；且**能解释为什么都是 0**（不是"都没数据"而是"都因缺标签而正确过滤"）。
    - 回归：`/v1/service` mine=9 vendor=9 通过。
    - 改动：实体加 `appName`；端口 `appTypeOf`→`labelOf(snapshot,key)`；`matches(type,name,appType,appName)` **两条分支都要过 `isGaopApp`**（厂商是统一加在 return 上的）；新增用例断言"有 appType 也照样要过 appName 这关"。
    - **三轮串起来**：55 我写成"依赖 TenX"（错）→ 57 用同源 9 条数据否掉、改成"未定论"（对了一半）→ 58 逐行读完找到真因（对）。**每步都不是"想更久"，而是又多了一条证据**；"读一半就下结论"与"抄错字符"同类，都缺一次核对。
  - **`serviceAuth` 算法已读全并留档**（[findings/59](docs/findings/59-service-auth-algorithm.md)）：下一轮直接实现，无需再读源码。
    - 三段过滤：①`appName` 既不是 `gaop` 也不是 `gis-server`（**与 `gaop` 端点正好互补，别写反**）；②keyword 两段式（与 gaop 同款）；③`authType` 过滤（有授权行→按 `auth.getAuth()` 命中；无授权行→三条分支，第 2 条已按等价式化简）。
    - 分页 + 装配与 `GET /v1/service` **完全同一套**（Pod 列表 + `serviceAuth` 富化）⇒ 形状复用已对拉的 `ServiceListResponseDTO`。
    - **下一轮最容易验证的点**：不带 `authType` 时本环境应为 **9 条**，可拿到**非空对拉**。
    - 实现清单 5 步已列全（实体加 `serviceType`、授权仓按名批量查、域服务方法、控制器、四种情形对拉）。
  - **`serviceAuth` 已实现且四种情形对拉全部一致**（[findings/60](docs/findings/60-service-auth-endpoint.md)）：无 authType=9/9、authType=0 9/9、**authType=1 0/0**、authType=0,1 9/9、keyword>45 400。**三条授权分支各自都被验证到**，不是"恰好都是 9"。
    - 服务切片进度 **5/33**。易错点已避开：`gaop` 要 `appName=="gaop"`，`serviceAuth` 要 `appName!="gaop"&&!="gis-server"`（**互补**）。
    - 插曲：首次探测"无 authType"那条我方空响应，重跑（先验 health）后一致——是探测脚本抢跑，不是实现问题；**对拉前要先确认被测服务就绪**。
  - 下一轮程序：搬常量 → 实现 filter（type=2 照抄缺陷）→ keyword 两段式（兜底 `filterServiceName` 需先补读）→ 复用已对拉的 `ServiceList` 形状 → 对拉 type=1/type=2/带 keyword/keyword>45 四种情形。：我此前逐轮报的「162/174」是**数错了**（grep 只匹配到部分模块的汇总行，且 surefire 报告目录混有旧文件）。按模块汇总行精确求和：**33 个测试类、210 条用例全绿**。教训与「手抄不透明值」「只验形状不验值」同源——**计数也是要验证的量**，必须用可复算的命令。

- （K3 预研结论）`LicenceInfo.expired(code)` = `{end:-1, endIso8601:null, code, expired:true}`，其中 `code = 机器码 + Base64(软件key)`；**"没有激活码"这一支不需要密码学**，可以在做 `GET /v1/software/{id}` 时一并实现（本轮没写，因为详情端点没做就没有调用方，写了是死代码）。

## Not yet specified

<!-- 看得见、但还说不成一句精确问题的雾 -->

- 限界上下文的切分与优先级（等 03 的证据才能问准）
- K8s 客户端替换后 `TenxService` 诸转换语义（具名端口的对象形状、`netWorkExport` 空桩、`system/lbgroup`）在重写中如何定义
- 门槛过后的产品化演进边界：哪些字段/表可以重命名、何时拆服务（现在只确定"允许"，没确定"怎么"）
- 灰度替换路径（新旧后端并存、Ingress/网关切流、回退）
- arm64 镜像与离线 Maven 私仓（不影响兼容性，但上生产必须）
- 前端 259 个函数里无人调用的端点是否保留（`/v1/app`、`service_entry` 等）
- 单节点 4.9GB 内存下的重写后端资源画像与限堆参数

## Out of scope

<!-- 只在目的地之外，不 graduation；要做得另开一张地图 -->

- 厂商前端 gaop-web 的重写（已复刻为静态产物，本轮只当验收工具）。
- 其它 gaop-* 辅助组件（gaop-auth / gaop-gateway-auth / gaop-fdfs / gaop-image-uploader / gaop-startup / gaop-diagnostic）的重写——除非某个域的可替换性必须连带改它们；那会是**另一张地图**。
- 厂商部署包与 Ansible 资产的修复（包本身尾部截断，需重新获取）。
- `../PaaS` 目录里既有的前端与历史文档的整理（不属于本仓）。

---

## 现状总览（第 59 轮盘点）

见 [`docs/findings/67-state-of-the-rewrite.md`](docs/findings/67-state-of-the-rewrite.md)。要点：
- **已闭环**：K1 身份战位（19 端点）/ K2 软件域（6）/ K3 授权读写验 / 服务切片 **8/33**；各自附验证方式。
- **刻意偏离 8 条**全部记录在案（TTL、`name:null`、NPE 收紧、事务、`scale` 消息、口令置 null…）。
- **卡住的分四类且各有具体原因**：依赖缺失设施 / 厂商自身坏掉（已定位代码行）/ 破坏性写 / 规模。
- **沉淀 6 种验证方法**与**8 种反复出现的失误模式**（后者比前者更值钱）。
- 当前：46 端点映射、38 测试类 **236 条全绿**、81 提交、63 篇 findings。
- 下一步优先序：服务切片同模式推进 → K2 跨域（`/auth` 列表可对拉；CS 上传/导入导出需先定边界）→ K3 侧效应 → 把"不可验证项"整理成对外偏离清单。

- （**服务切片 13/33**）[POST /v1/service/startUp](docs/findings/83-service-startup.md)：独立 namespace（`paas-verify-su2`）e2e——`time=30` 写注解 + 注入 `startup` initContainer（**镜像 `192.168.1.52:30002/system_containers/curl-alpine:latest` 完全符合厂商公式**，command/env 都对）；`time=0` 走取消；`time<0` 与"服务不存在"各自 400。
  - 配置项来历：厂商 `initPodConfig.image` 的默认值是从**厂商运行中的 jar** 里读出来的（`${INITIMAGE:curl-alpine:latest}`）；我们用同名配置 + `server.host.harbor`。
  - **循环里是双分支**（容易只写一半）：`time=0` → 删注解 + 清 initContainers + **整份 replace**；`time>0` → 写注解 + 注入容器 + **merge patch**。`cancelStartUp` 相当于只做 `time=0` 那一支。
  - 测试 270 → **273 条**全绿。
- （**服务切片 12/33**）[POST /v1/service/cancelStartUp](docs/findings/82-service-cancel-startup.md)：独立 namespace（`paas-verify-su`，用完即删）e2e——注解 `gaop/serviceInitTime` 与 `initContainers` 都被清掉；不存在的服务 → 400 且文案与厂商逐字相同（只差服务名）。
  - 三处照抄：**整份 `replace` 而不是 patch**（与 `hostName`/`priority`/`patchCommand` 的 merge patch 不同）；`initContainers` 置 **null**（不是空列表）；注解只在 `annotations != null` 时删。另：列表里一个失败就**中断**。
  - 测试 268 → **270 条**全绿。
- （**服务切片 + 影像域依赖地图**）[GET /v1/service/restartInfo](docs/findings/81-service-restart-info.md)：三种情形**对拉完全相等**（`gaop-web`/`gaop-gateway-auth` 用库里已有真实数据、`no-such-svc` → 空数组）。
  - 两处照抄点：键顺序 `podName → createTime → restartCount`（厂商声明顺序，我第一版后两个写反）；只取三列、无记录是**空数组**。**服务切片 11/33**。
  - **影像域依赖地图（本轮顺带查清）**：只有 `projectList` 是**纯库读取**（已对拉）；`repositories`/`allProjectImageList`/`existRepositories`/`getImageTagList` 都走 **Docker Registry API**；`imageStorage` 走 **SSH** 取磁盘（厂商自己也连不上 ⇒ 0/0）。⇒ 影像域可验证部分**基本到 projectList 为止**，其余属"依赖外部设施"，**不要在没有对端时硬写**。
  - 测试 266 → **268 条**全绿。
- （**影像域落地**）[GET /v1/image/projectList 实现](docs/findings/78-image-projectlist-implemented.md)：三步照抄（Harbor 全量 → 过滤 `system_containers`/`system_store` → `creation_time` **字符串**倒序 → 分页），配置项与厂商同名（`server.host.harbor`/`docker.username`/`docker.password`，**仓库不写口令**）。
  - **最容易写错的一处**：契约键是 **snake_case**（厂商 `@JsonProperty`），域对象是 camelCase ⇒ 用 `ImageProjectAssembler` 显式 Map 固定 11 键名称与顺序，并写**回放测试**（从实测样本读键集合断言一致）。
  - 两处照抄：鉴权头是 URL-safe base64（厂商 `getUrlEncoder()`）；`creation_time` 是 String 且可能畸形（实测 `+58753-04-29…`），不解析成时间。
  - **没有在线对拉（如实标注）**：`192.168.1.52:30002` 本机直连超时、Harbor 不在本集群 ⇒ 后端跑在本机时到不了 Harbor。正确性靠"逐行对应 + 回放测试"；要在线对拉需把后端**部署进集群**（拓扑变更，已记待办）。
  - 测试 262 → **266 条**全绿（+3 域规则 +1 键名回放）。
  - ⚠️ **上一段的实现数据源是错的（第 71 轮更正，`findings/79`）**：`NodeVersionServiceImpl.getImageServiceInstance():81-83` **写死返回 `k3sImageService`** ⇒ 实际生效的是 **`K3sImageServiceImpl`（查库 `g_image_group`）**，而不是我照着实现的 `K8sImageServiceImpl`（Harbor）。集群内（两个命名空间）实测 curl Harbor **HTTP 000 连不上**，也证明那两个项目不来自 Harbor。
    - K3s 逻辑与实测完全吻合：查 `g_image_group`（**为空则创建** `system_containers`/`gaop`/`library` 三个内置项目）→ **只过滤 `system_containers`** → `ImageGroup{id,createDate,name}` 映射成 3 个非空字段、其余 8 个 null → `total=list.size()`。
    - 顺带解释畸形值：`Instant.ofEpochSecond(createDate)` 把**毫秒**当**秒** ⇒ 年份爆到 58753（厂商缺陷，照抄勿修）。
    - **教训**：不是读错了代码行，而是**读错了类**——凡有多实现/多策略，先找"选择逻辑"再读被选中的实现。
    - 待办：按 K3s 实现重写该端点（**注意它会在表为空时写库**，对拉后要处理/清理）。
  - **已按 K3s 重写并在线对拉：整个响应体逐字节相同**（[findings/80](docs/findings/80-image-projectlist-k3s-verified.md)）。六步照抄：查 `g_group`(create_date 倒序/分页) → 空则取/建三个内置项目(写库) → 库行追加 → **只过滤 `system_containers`** → 只设 3 个字段其余 null → `total=过滤后条数`。
    - 实测：`?page=1&size=20` 与 `&keyword=gaop` 两种入参**整个响应体完全相等**（含 `+58753-04-29…` 畸形时间与 project_id）。
    - **顺带清理**：删掉上一轮那套 Harbor 实现（gateway/port/端口接口/yml 配置）——参考环境里 Harbor 路径不可达，留着是死代码。
    - **副作用**：表为空时该端点**写库**（建 3 个内置项目）。本环境 `g_group` 已被厂商写入 3 行，本次对拉未触发写入（清理时核对仍是 3 行）。
- （**顺着 e2e 线索找到真实缺口**）[GET /v1/image/projectList](docs/findings/77-image-projectlist-divergence.md)：A/B 共享端点后定位到它——**vendor 返回 2 个 Harbor 项目（library/gaop），我们返回空列表（stub）**。其余共享端点（quickStart/mode/lang/settings/switchRole）全部相等。
  - 线索链：HTML 大小差异 → 可见文本**完全相同**（否掉"多导航条目"的猜测）→ `data:image` 5 vs 15（导航图标）→ **前端调用的端点集合两侧完全相同** → 逐个 A/B → 命中 `projectList`。
  - 实现路径：`K8sImageServiceImpl.projectList:107-119` = `harbor.allPageProjects` → 过滤 `system_containers`/`system_store` → 按 `creation_time` 倒序 → 分页。
  - **重要更正**：`findings/52` 把 `imageStorage` 归为"依赖缺失的 Harbor 设施"，但本轮证明 **Harbor 是通的**（厂商从它读出了真实项目）⇒ **影像域应从 C 类挪出，变成可实现的域**（有真实对端、能对拉、前端可见）。
  - 下一轮：找 Harbor 地址/凭据来源 → 实现 projectList（预期逐字节相同）→ 顺带做 imageStorage。
- （**端到端复验**）[第 68 轮全栈复验](docs/findings/76-e2e-reverification-round68.md)：距上次 e2e 约 30 轮之后，用**厂商原样前端 + 我们的后端**重跑用户管理页——**表格内容与厂商逐字节相同**（表头 8 列、数据行 `1/admin/管理员/运维管理员门户/添加子角色/…`），前端调到的端点**全部 200**。
  - 两处差异：①WS 通道（我们走 SockJS xhr 回退、厂商走原生 WS，属 harness 代理方式，`findings/20` 已覆盖）。
  - ②HTML 长度（55KB vs 101KB）——**第 69 轮已查清，且上一版的"厂商多若干导航条目"是错的**：两页**可见文本都是 64 行且完全相同**（导航标签两边都在）；真正差异是 **`data:image` 内联图片 5 vs 15**——多的 10 个在**左侧导航项的图标**上（`CH_app_icon/CH_Img`），即**导航项在我们这边没有图标**；`<script>`/`<style>` 两侧完全相同。HTML 已归档。
  - **可查线索（下一轮）**：导航项图标由哪个端点提供？（候选：菜单/权限端点、`/v1/pub/quickStart`）——一个**具体的、可定位的**缺口。
  - 意义：**30 轮改动没有破坏既有链路**——这正是"复验"要证伪的东西。截图归档到 `docs/findings/artifacts/`。
- （**K2 跨域**）[POST /v1/software/software/active](docs/findings/75-software-activate.md)：**用自造 RSA 密钥对 + 临时 `g_auth` 公钥行**在真实环境验证批量激活，**五种情形一致**。
  - 手法（沿用 K3）：`openssl genrsa` → 临时插一行 `default_auth`（`create_at` 取最大 ⇒ 厂商 `get(0)` 选中我们）→ 手工签 RS512 JWT → 验完删行并核对原行完好。
  - 坑：**`g_auth.id` 是自增 int**，第一次用字符串 id 插入静默失败 ⇒ 五条用例全落到"验签失败"；是"`default_auth` 行数没变成 2"这个反常现象暴露的。
  - **与 `cancelAuth` 三处相反/易错**：`result` 恒 **true**（不是 false）；全部 id 不存在 → **空数组**（不是 400）；缺公钥行 → 400 `auth.invalid.public.key.auth.code`。另：验签失败**只折算一个键** `software.parse.activation.code.failed`（不像 `checkActiveCode` 细分）；`active_create` 用**请求起始时间戳**；写入**不动 `updated_at`**。
  - 测试 259 → **262 条**全绿。
- （**K2 跨域**）[POST /v1/software/cancelAuth](docs/findings/74-software-cancel-auth.md)：**五种情形对拉一致**（有激活码/无激活码→400/混合/不存在/先有效后不存在），并核对库内状态。
  - 两处照抄点：**没有激活码是"跳过"**（只有结果列表为空才 400 `auth.no.software.authorization`）；**这条链路没有事务**——"先有效后不存在"实测两侧库里都已被清空（先清空再抛错、不回滚）。
  - **引号家族的第四次坑**：`python3 - <<PY`（未加引号）里的 SQL 含**反引号**被 shell 当命令替换 ⇒ `reset()` 静默失效、输出看起来像"五条全不一致"。规则：含反引号/`$`/三引号的脚本一律用**带引号的 heredoc**或独立脚本文件，并确认它真的执行了。
  - 测试 254 → **259 条**全绿。
- （**K2 跨域**）[POST /v1/software/softwareCode](docs/findings/73-software-code-endpoint.md)：软件申请码，**输出与厂商逐字节相同**（两种入参 + 空列表 400）。
  - 为什么能逐字节：算法完全确定 `Base64(JSON({serviceIdList, machineCode}))`，**机器码那一版第 34 轮已逐字节验证过** ⇒ 基础件验证过、后面的端点免费拿到强证据。
  - **分层决定**：domain 模块没有 Jackson（第一版写在域层编译失败）⇒ 域服务只返回载荷对象，**JSON 组装与 Base64 放 trigger 层**，并用**显式 ObjectNode** 固定字段顺序（**字段顺序就是契约**）。
  - 顺带记：`GET /v1/software/{softwareId}/export` 第一步就调 `detailSoftware`（厂商自身 NPE，A1）⇒ 属 C 类（无法对拉），等详情端点的证据来源确定后再处理。
  - 测试 252 → **254 条**全绿。
- （**K2 跨域**）[PUT /v1/software/{softwareId}/service/{serviceName}](docs/findings/72-software-service-port-path.md)：端口映射路径，**七种情形对拉一致**（成功/路径占用/端口不存在/长度/首字符数字/非法字符/空白）。
  - **两处方法层面的教训**：①**共享状态的顺序对拉会自己骗自己**——第一版依次打两侧，第一种情形的**成功写入**污染了后续所有情形的前置状态，看起来"六条全不一致"；修法是**每种情形前都重置探针行**。②**错误响应不能整体比对**——`data.trace` 里的类名/行号天然不同（我们 `com.csm.paas.core` vs 厂商 `com.glriverside.gaop.be`），正确比法是 `code` + `msg` + 业务字段（并忽略自增 id）。**已写进方法清单。**
  - 照抄点：四个 path 校验**全在 controller 且在任何 DB 访问之前**；判重错误参数是 **serviceName**、端口不存在是 **port**（易写反）；`path` 先 trim；成功后**重新查一次**再返回。
  - 测试 248 → **252 条**全绿。
- （**K2 跨域**）[GET /v1/software/{softwareId}/service/{serviceName}](docs/findings/71-software-service-ports.md)：端口映射列表，**三种情形对拉完全相等**（两条/一条/不存在→空数组）。
  - 照抄点：只有 `software_id` + `service_name` 两个等值条件；**不排序**（厂商没写 orderBy，不"顺手加稳定排序"）；查不到返回**空数组**（不是 400）。
  - **同一 `/v1/software/...` 前缀下"找不到"有四种表现**（详情 400 `software.software.id.not.found` / 删除 400 `software.not.exist.by.id` / 本端点空数组 / 战位下用户空列表）——只能逐个核对，不能按"同模块应该一致"推断。
  - 测试 246 → **248 条**全绿。
- （**服务切片**）[PUT /v1/service/patchCommand/{serviceName}](docs/findings/70-service-patch-command.md)：独立 namespace e2e——`command`/`args` 作用到**所有容器**且真的改上；服务不存在 → 400（与厂商同码同文案模式）。
  - 照抄点：厂商返回 `Response.ok()` **不带 body** ⇒ 响应 `data:null`（不是 `true`）；**无 StatefulSet 回退**（与 `hostName` 不同）。
  - fabric8 枚举名易错：`application/merge-patch+json` → **`PatchType.JSON_MERGE`**（不是 `MERGE`）；`strategic-merge-patch+json` → `STRATEGIC_MERGE`。**两种 merge patch 不要混用**（JSON_MERGE 整份合并、STRATEGIC_MERGE 按键合并列表）。
  - 服务切片 **10/33**；测试 244 → **246 条**全绿。
- （**服务切片**）[GET /v1/service/otherPlugin](docs/findings/69-service-other-plugin.md)：**三类列表的 `namespace/name` 集合全部与厂商相同**（4/15/1，跨命名空间）——服务切片第二个非空对拉。
  - 实现取舍：fabric8 7 在"跨命名空间列举"上不便直接带选择器（`withFieldSelector` 与 `ListOptionsBuilder` 两版都编译不过）⇒ 改为全量列举 + **内存里严格复刻 k8s 选择器语义**（`!k` 要求键不存在；**`k!=v` 对缺失键也成立** ⇒ "什么标签都没有"的对象会被保留，这正是 vendor 那 15 个 Deployment 的来源）。
  - 风险用**对拉结果集合**兜底（本集群上语义等价已证明）+ 4 条单测钉住语义本身。
  - 与 `serviceAuth` 的对照：两者都是"列表"，但 `otherPlugin` 发的是**原始 K8s 对象**、**不能套装配器**。
  - 服务切片 **9/33**；测试 240 → **244 条**全绿。
- （**K2 跨域第一个**）[GET /v1/software/auth](docs/findings/68-software-auth-list.md)：**7 种情形对拉全部完全相等**（含三态探针、name 只打在 name 上、分页）。
  - **先踩了自己两个坑**，都是"顺手复用隔壁端点的查询"：①授权列表的 `likeName` **只打在 `name`**，我复用了列表端点的 `name OR key`；②修完①后 `total` 仍不一致，因为 `countAuthList` 复用了列表的 `count()` ⇒ **total 与 list 来自两套 WHERE**。已各补专用查询。
  - 同族第 4 例：`edit()`vs`patch`、`fixedName`vs`gaop/serviceLabel`、存在vs值为空、现在是"列表过滤"vs"授权列表过滤"——**复用相邻实现的代价，是在它们分叉的那一点静默出错**。
  - 反直觉但已确认：`active_expire = 0` → status **2**（第一分支先命中），`== 0 → 0` 那句在 now>0 时不可达；已用单测钉住。
  - 测试 236 → **240 条**全绿；探针已清、无残留。
- （**服务切片 14/33**）[POST /v1/service/{serviceName}/node_port 实现](docs/findings/86-node-port-implemented.md)：独立 namespace（`paas-verify-np`）**四种情形全通过**——A 推导（名字 `tcp-np-svc-0` 小写公式、nodePort 自动分配）、**B 容器无端口 → `data:false` 静默路径**、C 不存在 → 500（消息带服务名）、D 显式 nodePorts 生效。
  - **一轮写完、零返工**：因为上一轮已把 120 行读到可翻译（`findings/85`）。对照：`gaop` 边读边写连错两轮、影像域读错实现类整版返工——"先规格后实现"在本会话第二次零返工（第一次 `serviceAuth`）。
  - 测试 273 → **278 条**全绿。
- （**node_port 规格**）[findings/85](docs/findings/85-node-port-spec.md)：把 120 行的 `patchServicePorts` 读到"可直接翻译"（两个分支 + 异常映射 + 三处易错点 + 下一轮的实现与验证计划）。
  - 关键：**返回布尔**（`Response.ok(v1Service != null)`），A 分支"推导不出端口"时**返回 null ⇒ `data:false`**（静默失败）；`name` 公式 `<protocol>-<name>-<i>` **小写**；两分支错误消息不同（A 分支带服务名的中文 `SystemException`）。
  - 同族的 `POST /{serviceName}/nodePort` 只读到头部，**未逐行读完**，实现时不要把两者混为一谈。
- （**已实现**）[GET /v1/user/list](docs/findings/99-user-list-implemented.md)：**四组用例对拉完全相等**（分页/keyword/第二页/超长 keyword 400 文案逐字相同）。
  - 结构：新建 `IUserRolesQueryService`/`UserRolesQueryService`（`roles` 要复用 `/v1/role` 装配，塞进 `UserQueryServiceImpl` 会牵连 4 处测试构造）；域实体 `UserRolesEntity` **故意不含 `nickname`**（厂商从不 set ⇒ 让"填错"在类型层面不可能）；装配器复用 `RoleListAssembler.toItemDTO`、空集合兜底 `List.of()`；查询**不过滤 root**。
  - **如实标注**：`subRoles` 在本环境为 `[]`，非空形状**未对拉验证**（已映射全部标量字段，`userList`/`roleList` 未映射）⇒ 出现带子角色的用户时需按样本补齐。
  - 端点映射 61 → **62**；测试 289 → **293 条**全绿。
- （**user/list 规格**）[findings/98](docs/findings/98-user-list-spec.md)：与 `/v1/user/info` **同一个查询、不同条目形状**——`UserInfoList`(11 字段) vs `UserRoles`(16 字段)；字段差已逐项列出：去掉 `belongUsers`、加上 `email/birth/phone/avatar/uuid` 与布尔 `admin`。
  - 下一轮要改的点已定位：`UserInfoEntity` 缺 5 个字段（+ `IUserDao` 查询补列）→ 新增 `UserRolesDTO`/`UserRoleListResponseDTO`/装配器（`admin` 由 roles 是否含 `"admin"` 推出）→ 控制器加映射 → **关键字长度校验**（照抄"带 % 算长度 > 38"）。
  - 验证计划：分页/keyword/超长 keyword(400)/第二页 四例对拉。注意 `/v1/user/list` **不返回口令哈希**，无偏离。
  - **读 `getUserRoles:1780-1803` 后的三处关键修正**（比字段表更易踩）：① **`nickname` 恒 null**（厂商没 `setNickname`，尽管字段声明了）——别顺手从实体填；② **`roles`/`subRoles` 是对象列表**（`List<Role>`/`List<SubRole>`），而 `/v1/user/info` 同名键是**字符串**（已逐字节验证）⇒ 实现前必须先取 `Role`/`SubRole` 的序列化样本；③ **不过滤 root**（`/v1/user/info` 才过滤）⇒ 我们的 `queryUserInfoPage` 不能复用，需新域方法。
  - 好消息：`selectPageByKeyword` 的 SQL **已取全 14 列** ⇒ **不用改 SQL**，只需给 `UserInfoEntity` 补 5 字段并在 `toInfo` 填上。
  - **已取厂商实时样本**（`runtime-samples/v1_user_list.json`）：`nickname` 实测 null ✅；**`roles` 是完整 16 键 Role 对象**（与 `/v1/role` 同形状）⇒ 实现要**复用角色域装配**（含 `softwareList`/`userList`/`subRoleList` 填充规则），**不是"加字段+DTO"的规模**；**`subRoles` 是 `[]` 不是 null**（与 `/v1/user/info` 写法不同）。⇒ 真实成本 ≈ "用户分页 + 每用户套一遍角色装配"。
- （**改动后 e2e 复验**）[findings/97](docs/findings/97-e2e-after-token-fix.md)：第 89 轮把 `/v1/user` 的 token 改成 null 后，专门重跑 e2e——**表头/数据行/可见文本与厂商归档页全等**（64/64 行、仅 vendor 有=空）、前端请求**无 4xx/5xx**、脚本退出 0。⇒ 修复没破坏前端。
  - 经验：**改了前端会读的端点就必须重跑 e2e**——契约比对（L1/L2）只看字段对不对，看不出"页面会不会白屏"。
- （**工具修正**）[contract-check 增加易变字段模式](docs/findings/96-contract-check-volatile.md)：`/v1/user/info` 的 `createdAt`/`updatedAt` 标为 `mode: volatile`——**只比存在与类型**，不再因样本陈旧误报（**不用 `allowDeviation`**：那是"允许的偏离"会永久豁免，而"易变"只是字段性质）。
  - 用**上一次会误报的那份实时响应**重跑验证 ⇒ 现在 `L1+L2 全部通过 ✅`。
  - 顺带发现工具自带**镜像一致性自检**（`docs/findings/read-paths.json` 与 `paas-core-trigger/src/test/resources/vendor/read-paths.json` 必须一致），已同步。
- （**契约比对复跑**）[findings/94](docs/findings/94-contract-check-rerun.md)：9 条已记录读路径跑 `tools/contract-check.py` ⇒ **7 条 0 失败**（service/role/sub_role/role-list/software 全绿）。
  - **真差异（B11，已修）**：`GET /v1/user` 的 `data.token`——厂商 **null**、我们填了会话 token（厂商只在 `login` 里 `setToken`）。⇒ [findings/95](docs/findings/95-user-token-fixed.md)：`AuthServiceImpl.currentLogin`（**只被 `/v1/user` 使用**）去掉 `.token(token)`，契约比对 **L1+L2 全部通过（❌=0）**；并**先验证登录响应仍带 token、Set-Cookie 一致**，确认没把相邻正确行为改坏。
  - **工具性问题**：`/v1/user/info` 的 `createdAt/updatedAt` 差异是**样本陈旧**（期间 admin 行被探针改过）⇒ 比对前要么重抓样本、要么显式排除时间类字段。
- （**已实现**）[GET /v1/user/access_permission](docs/findings/93-access-permission-implemented.md)：**六组用例对拉完全相等**（含 `/whatever` → true：厂商正则里 `[tenx]*`/`[gaop]*`/`[v1]*` 是**字符类**、可匹配 0 次，照抄才一致；改成 `(a|b)` 写法就会不一致）。
  - 无 Cookie：厂商 **500（NPE）**、我们 400 `user.not.logged.in.or.expired`（厂商本意）⇒ B 类偏离。
  - 结构：新建 `IUserAccessService`/`UserAccessService`（按规格建议接线，**不动** `UserQueryServiceImpl` 的 4 处测试构造）。
  - 端点映射 60 → **61**；测试 285 → **289 条**全绿。
- （**access_permission 规格**）[findings/92](docs/findings/92-access-permission-spec.md)：算法逐行读全（取**最后一个**战位 → 该战位关联软件 → `/api/<key>/<path>` → `findPath` 三个**照抄的正则**）。
  - **厂商缺陷**：没 Cookie 时 `userLoginInfo` 非 null（先 new 的）⇒ 那句 `if (userLoginInfo == null) throw` **不可达**，实际是第 1432 行 **NPE(500)**；它本意是抛 `user.not.logged.in.or.expired`。
  - 本轮已落地要用的 SQL：`selectServicePathsBySoftware`（`g_software_app`⋈`g_software`⋈`g_service_port` 三表 join，照抄）。
  - **下一轮的接线**：`UserQueryServiceImpl` 缺 `ISoftwareRepository` ⇒ 推荐新建 `UserAccessService`（不动既有 4 处测试构造），而不是加构造参数。
  - 验证计划已列（admin+/api/gaop/v1/... → true；`/` → true；`/whatever` → false；无 Cookie 记 B 类偏离）。
- （**认证审计 + K1 剩余清单**）[findings/91](docs/findings/91-k1-remaining-and-auth-audit.md)：厂商只有 Role/User/Public 三个控制器要会话（`getCurrentUser` 读 **Cookie**）；**服务域全部端点都不需要会话**（所以我们那句"原型尚未实现认证"是对的，不是缺口）。
  - 发现一处差异（B9，已入 deviations）：`GET /v1/role/software` 无 Cookie 时厂商 401、我们返回 `{list:[],total:0}`。
  - **K1 剩余端点清单**：`/v1/user/list`、`/redirect`、`/access_permission`、`/tenx_user` + **文件类**（`recovery`/`backup`/`export`/`import/template`/`import`/`update_avatar`）+ **破坏性** `reset_user`。⇒ K1 剩下主要是文件传输类，值得单独一轮（牵动 `g_upload_store_record` 与 `gaop-fdfs`）。
- （**K1 边界**）[GET /v1/role/{roleId}/software](docs/findings/90-role-software-list.md)：实现"管理员校验 + 分页 + `order by \`order\`` + `{list,total}`"；`appName` 的 i18n 与 `url` 两种拼法**未实现**（库里 0 行无法对拉那两段）。
  - **顺带修掉一个潜伏问题**：`g_role_software` 的查询原来**没有 ORDER BY**，而厂商是 `order by \`order\``——因为库里 0 行，`/v1/role`、`/v1/role/list`、`/v1/role/software` 三个已完成端点从未暴露该差异。**这是"零行数据下的假通过"**，已给该查询补上排序（同时修了那三个端点）。
  - **（第 84 轮更正）认证路径其实能对拉**：真因是**我把 token 发错了地方**——厂商 `getCurrentUser(req):883-903` 从 **Cookie** 取 `gateway-token`，我当时用的是 HTTP 头。改用 Cookie 后厂商 200，且四组用例（两个战位/带 keyword/不存在的战位）**响应完全相等**（含"不存在的战位 → `{list:[],total:0}`，不是错误"）。
    - **方法教训**：请求 401 时先确认"token 该放 Cookie 还是 Header"，再对端点下结论。这是本会话第二次"看起来厂商坏了、其实探针错了"（第一次 `gaop` 空列表）。
    - 事实：厂商同应用里两套取 token 方式并存——`/v1/user/info` Cookie/Header 都过（200/200），`getCurrentUser` 路径**只认 Cookie**。
  - 测试 283 → **285 条**全绿。
- （**服务切片 15/33**）[POST /{serviceName}/nodePort 实现](docs/findings/89-nodePort-implemented-vendor-broken.md)：实现完成，**并发现这个端点在厂商侧无法成功**。
  - 实测三种输入：`type=ClusterIP`+带 nodePort → K8s 422「nodePort Forbidden when type is ClusterIP」；不带 nodePort → K8s 422「spec.ports: Required value」；`type!=ClusterIP` → `data:false`。⇒ **永远拿不到 `true`**，且 `service.port.occupied.error` 是**误报**（真实原因是 ClusterIP 不允许 nodePort）。**照抄其可观测行为，不修正**。
  - 主 Service 部分正确（type 写死 ClusterIP、ports 推导、nodePort 显式 null）；`<name>-nodeport` 按 `generateServiceDoc` 骨架创建/patch；随机端口 [10000,75534]。
  - 分层：domain 不依赖 fabric8 ⇒ 新增 `K8sApiException`，由 port 翻译 fabric8 异常。
  - 单测暴露并修复一个隐患：`listUsedNodePorts` 返回集合被直接 add ⇒ 改为先复制。
  - 测试 278 → **283 条**全绿。
- （**nodePort 规格**）[findings/88](docs/findings/88-nodePort-spec.md)：把 ~150 行读到"可直接翻译"。
  - **与 `node_port` 不是近重复**：本端点还管**另一个 `<name>-nodeport` Service**（不存在则 `generateServiceDoc` 造、存在则 patch）。
  - 四处怪点：主 Service 的 type **写死 `ClusterIP`** 而 `<name>-nodeport` 用 `cfg.type`；**`nodePort` 为空时随机分配**（[10000,75534]、≤10 次重试）⇒ **结果不可复现**，对拉只能比结构/区间/不冲突；`cfg.type != "ClusterIP"` 时**返回 false**；步骤 3 里 `nodePort` 是**显式置 null**。
  - 这一步**有** StatefulSet 回退（与 `node_port` 的 A 分支不同）。已给五步独立 namespace 验证计划。
- （**剩余端点规模地图**）[findings/87](docs/findings/87-remaining-endpoints-scale-map.md)：把 7 个候选逐个读到"能估规模与依赖"——**没有一个能在半轮做完**。
  - `serviceInfoByNode` **卡在 `getServiceItemInfo`（监控子系统）**，建议明确降级；`nodePort` 管的是另一个 `<name>-nodeport` Service（~150 行）；`patchService` ~100 行；`updateVolumes` ~120 行；`/v1/role/{roleId}/software` ~60 行（CS url + Deployment 现场状态）；`relationService` ~80 行（mapping info + **一处厂商 NPE**）。
  - 优先级：`nodePort`（与 `node_port` 同业务）→ `/v1/role/{roleId}/software`（K1 最后一块读）→ `patchService`/`updateVolumes`；影像域其余**不建议动**（无对端）。
- **第 76 轮现状总览**：[findings/84](docs/findings/84-state-of-the-rewrite-round76.md)（接替第 59 轮的 67）——57 端点映射（服务 13/用户 12/软件 10/战位 6/子角色 1/授权 5/Shell 6/Auth 2/影像 1/健康 1）、273 条用例全绿、104 提交、80 篇 findings。
  - 第 59→76 轮补上：K2 跨域 6 项（含自造密钥对的批量激活）、服务切片 13/33（写端点六个有独立 namespace e2e）、影像域 projectList（按 K3s 重写后逐字节对拉）、全栈 e2e 复验。
  - 新增三条方法教训：**多实现先找选择逻辑**（读错类）、**配置默认值从运行产物读**（厂商 jar 里的 `initPodConfig.image`）、**假差异比漏测更危险**（共享状态顺序对拉 / `trace` 不可比）。
  - 卡住的分四类：外部设施（registry/SSH/Harbor）、厂商自身坏、破坏性写、规模（`node_port`/`patchService`/`relationService`）。
- **对外交付版偏离清单**：[`docs/deviations.md`](docs/deviations.md) —— A 类 11 条（厂商自身缺陷/环境依赖）、B 类 8 条（我们主动收紧）、C 类 6 条（参考环境导致无法对拉）。每条都指向 findings 里的实测证据，便于核对判定依据。**这份清单是本阶段最后一件可直接使用的东西**（此前这些信息散落在 63 篇 findings 里）。
