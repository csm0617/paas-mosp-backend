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
