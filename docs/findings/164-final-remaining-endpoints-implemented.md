# 最后 39 条端点一次性实现（第 167 轮）

按用户要求把**剩余全部**一次做完。这一轮先修了审计工具的一个解析缺陷（它把 5 条**已经实现**的端点
误判成缺口），再用 4 个写域互不重叠的并行 agent 把真正的 39 条补齐。

## 1. 先修工具：跨 javadoc 吞注解导致的假缺口

`tools/coverage-audit.py` 直接在**带注释**的源码上跑注解正则。javadoc 里常写厂商注解原文
（例：`` 厂商 `@PostMapping("/delete")` backupInfoDelete ``），正则从注释里的注解一路吞到**真注解**，
再被 `_values` 的 `value=` 分支抓成别的字符串 ⇒ 该端点被算成"未实现"。
`/v1/backup` 的 `delete`/`reset`/`singleBaseData`/`singleDownload`/`singleReset` 五条就是这样被漏算的。

修法：解析前先把注释替换成**等长空白**（状态机跳过字符串字面量，避免误伤 `"http://..."`），
位置不变所以 `class` 定位仍准。修正后口径：已实现 **216 → 220**，未实现 **48 → 43**。

## 2. 本批 39 条的交付

| 写域 | 条数 | 内容 |
| --- | --- | --- |
| Cicd + pub 重逻辑 | 11 | `pub/task`、`cicd_v2`、`cicdList`、`cicdlog`、`updateTask`、`delete`（新表 `g_ci_cd`）、`authing`、`import`（整站备份 zip）、`service_status`、`serviceStatus`、`patchResource` |
| 文件域 | 10 | `pub/file/{id}`、`file/delete`、`file/download/{id}`、`file/list`、`file/storage`、`file/upload/{projectId}`、`fdfs/upload/{projectId}`、`fdfs/status`、`file_project`、`file_project/{projectId}` |
| 监控 + service | 6 | `pub/nodeMonitor`、`nodeUsage`、`serviceMonitor`、`DELETE /v1/service/node`、`PUT /v1/service/patch`、`GET /v1/service/serviceInfoByNode` |
| 整包导入导出 + 其它服务 | 12 | `software/backup`、`v2/backup`、`import`、`{id}/updateRemoteCs`、`software/relationService`、`database/export`、`database/user`、`user/{id}`、`user/revoke_authorization`、`image/upload`、`image/clean`、`user/getLoginUser` |

## 3. 合并验证（Lead 统一做）

| 项 | 结果 |
| --- | --- |
| `mvn -o test-compile` | **BUILD SUCCESS**（修 1 处：域服务 `DatabaseUserServiceImpl` 用了 `@Transactional` 但 `paas-core-domain` 没有 spring-tx ⇒ 给该模块补 `spring-tx` 依赖，保留厂商的事务语义） |
| `mvn -o test` | **656 测试 0 失败** |
| 应用启动 | `health=UP`，**零** `BuilderException`/`UnsatisfiedDependency`/`BeanCreation`/`Ambiguous mapping` |
| 路由冒烟 | 前端面 ∩ 已实现共 **226 条路径**逐条 GET：**0 条 404**（107×500 / 63×200 / 55×400 / 1×401） |
| 功能抽样 | `file_project`→真实种子行、`file/list`→`{fileItems:[],total:0}`、`fdfs/status`→两个 tracker 的 status=99 占位（厂商回落分支）、`service_status`→真实服务状态列表、`serviceInfoByNode`→20 键空页、`getLoginUser`→未登录 `data:null` |

`/v1/database/user` 在本环境返回 `BadSqlGrammarException`：厂商那段查 `mysql.user`，用的是**第二个
数据源（`spring.datasource.third`，k8s 下甚至是另一台主机）**，本仓只配了主数据源、且 `gaop` 账号对
`mysql.user` 无权限（实测 `ERROR 1142`）⇒ 属**类 C**（已在 `IDatabaseRepository` javadoc 写明缺哪一段）。

## 4. 最终覆盖率

| 指标 | 本轮前 | 本轮后 |
| --- | --- | --- |
| 前端调用面 | 232 | 232 |
| paas-core 已实现 | 220 | **259** |
| 未实现（前端面） | 43 | **6** |

剩下 6 条**没有一条是真实端点缺口**：

| 路径 | 判定 |
| --- | --- |
| `/v1/mail/status`、`/v1/pods`、`/v1/persistentvolumes`、`/v1/thirdPart/getUnreadCount` | **口径噪声**：厂商 controller 全仓 grep 不到，前端 bundle 里指向别的应用（与 `/zk-102-jsxx-back-8304/...` 同源） |
| `/v1/pub/file/download`、`/v1/pub/fdfs/upload` | **字面量残片**：真实调用是 `"/v1/pub/file/download/".concat(id)` 与静态串 `"post /v1/pub/fdfs/upload/"`，已被 `{fileId}` / `{projectId}` 映射覆盖（实测 `ours` 里两条都在） |

⇒ **按前端调用面口径，端点缺口为 0。**

## 5. 类 C 清单（本批新增，代码齐全、本环境不可验证）

- **MinIO**（6 条）：`MinioS3HttpClient` 用 `java.net.http` + 手写 AWS SigV4（离线仓库无 `io.minio`，未改 pom）；
  `file/storage` 走的是 MinIO **Admin API**（非 S3），取不到即回落空数组。
- **FastDFS**（2 条）：`fdfs/status` 走配置回落（离线也返回厂商同形）；`fdfs/upload` 的**私有二进制协议未实现**
  （官方无 HTTP 上传接口），直接抛厂商同文案错误，javadoc 写清缺的三段。
- **Cicd**（6 条）：需 `ci-job-image` 镜像、`cur-etcd` 节点标签与真实 Job pod。
- **Prometheus**（3 条监控 + `serviceInfoByNode`）：`IPrometheusMetricPort` 扩了 range/vector 查询。
- **SSH / docker / registry / Redis / MySQL 管理连接**：`updateRemoteCs`、`image/upload|clean`、
  v2 备份与导入的镜像段、`database/user` 的管理 SQL。
- **破坏性（照抄、未做在线实验）**：`DELETE /v1/service/node`、`PUT /v1/service/patch`、`pub/import`、`software/import`。

## 6. 本批新增依赖与测试库 schema

- `paas-core-domain` 加 `spring-tx`（BOM 管版本）——只为 `DatabaseUserServiceImpl` 的 3 处 `@Transactional`
  （本仓既有做法是放在 infrastructure 仓储，但厂商语义在服务层，这里按语义保留）。
- `paas-core-app/src/test/resources/test-schema.sql` 补：新表 `g_ci_cd`（厂商 gaop-9.sql:167-182 原样 13 列）
  与 `g_service_auth` 的 `auth_code`/`created_at`/`updated_at` 三列（`authing` 的 upsert 会写）。

## 7. 仍未移植（显式登记，不伪造成功）

- `software.json` → 厂商 `SoftwareService.importSoftwareInfo` 的**整站备份**分支：`/v1/software/import` 已实现，
  但 `/v1/pub/import` 里那条调用路径由 Cicd/pub 域的 `IPubImportSupport` 记 WARN 后跳过（跨域，未回接）。
- `databaseUser.json` → `g_database_user` 未建模到「整站备份」语义里。
- `user.xls` 的 `isImportFullBackUp=true` 语义（本仓 `IUserImportService.importUsers` 只有 2 参）。
