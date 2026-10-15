# `g_cicd` 六条 + `/v1/pub` 五条（authing/import/service_status/serviceStatus/patchResource）实现

**证据等级**：〔代码事实〕逐行读厂商 `CicdController.java`、`CicdService.java`、`PublicController.java`、
`PublicFdfsController.java`、`FileService.java`、`AuthService.java`、`TenxUtils.java`；
〔静态编译〕全仓 `javac`（见 §6）；〔类 C〕K8s Job / Docker Registry / TCE 开放 API / 宿主目录不可验证。
2026-10-15。

本批正好是 `findings/162` §3 里列为"明确未做"的两组：
「需新数据源/新表 → Cicd 6 条（需 `g_cicd` 表）」与
「外部依赖（类 C）→ `/v1/pub` 的 `authing`/`import`/`service_status`/`serviceStatus`/`patchResource`」。

## 1. 实现路径清单（11/11）

| # | 端点 | 层 | 厂商出处 |
| --- | --- | --- | --- |
| 1 | `POST /v1/pub/task` | `CicdController` → `ICicdService.createTask` → `ICicdRepository` | `CicdController:38-43` / `CicdService:281-305` |
| 2 | `POST /v1/pub/cicd_v2`（multipart） | `CicdController` → `ICicdService.continuousIntegrationsV2` → `ICicdBuildPort` + 宿主机目录 | `CicdController:53-59` / `CicdService:428-521` |
| 3 | `POST /v1/pub/cicdList` | `CicdController` → `ICicdService.list` → `ICicdBuildPort` + `IDockerRegistryPort` + `IImageCatalogQueryService` | `CicdController:61-66` / `CicdService:184-279` |
| 4 | `GET /v1/pub/cicdlog` | `CicdController` → `ICicdService.getJobLog` → `ICicdBuildPort.readJobLog` | `CicdController:68-73` / `CicdService:157-182` |
| 5 | `DELETE /v1/pub/delete` | `CicdController` → `ICicdService.deleteTask` | `CicdController:75-80` / `CicdService:125-135` |
| 6 | `POST /v1/pub/updateTask` | `CicdController` → `ICicdService.updateDockerFile` | `CicdController:82-87` / `CicdService:137-155` |
| 7 | `POST /v1/pub/authing` | `PublicExtraController` → `IPubAuthingService` → `IAuthInfoRepository` + `IPubAuthWritePort` + `IPubClusterPort` + `IServiceQueryService.batchRestartServices` | `PublicController:1961-2027` / `AuthService:210-230`、`:313-325` |
| 8 | `POST /v1/pub/import`（multipart） | `PublicExtraController`（zip/JSON 解析，同 `DataBaseController` 的分工）→ `IUserImportService` / `IRoleImportService` / `ISubRoleImportService` / `IServiceQueryService.importConfigMaps` / `IDatabaseService.importDataBase` / `IPubImportSupport` | `PublicController:3369-3576` |
| 9 | `GET /v1/pub/service_status` | `PublicExtraController` → `IPubServiceStatusService.allServiceStatus` | `PublicController:442-459` / `TenxUtils.getStatus:49-74` |
| 10 | `GET /v1/pub/serviceStatus` | `PublicExtraController` → `IPubServiceStatusService.serviceStatusInfo` | `PublicController:2428-2614` |
| 11 | `POST /v1/pub/patchResource` | `PublicExtraController` → `IPubResourcePatchService` → `IPubClusterPort` | `PublicFdfsController:196-202` / `FileService:1138-1245` |

厂商 `POST /v1/pub/cicd`（`@Deprecated` 的 fastdfs 老路径）**不在本批**（不在清单里），故未实现。

## 2. 照抄点（重写最容易走样的地方）

### 2.1 Cicd

1. `updateDockerFile`：`dockerfile == null` 时**改了内存对象就 return false**，`desc`/`updateAt` 都不落库
   （`CicdService:148-154`）。
2. `cicdList`：先 `BeanUtils.copyProperties(c, cicdWithPodInfo)` **再**在 `c` 上算新 `jobStatus` ⇒
   **响应里的 `jobStatus` 是入库前的旧值**，而库里已是新值（`CicdService:217-251`）。
3. `cicdList` 的 `succeeded` 分支：镜像 tag 列表为 null/空时**什么也不写**（`CicdService:238-245`）。
4. `cicd_v2` 把 Dockerfile **第一行整体替换**为 `FROM <harbor>/system_containers/<第一行去 FROM 与空格>`
   （`CicdService:447-453`）。
5. `cicd_v2` 创建 Job 后读状态：四支全不命中时只记日志、**不写 jobStatus**，但仍写 `update_at`；
   若返回的 `status` 为 null，厂商 NPE 被外层 `catch (Exception)` 吞掉 ⇒ **连 `update_at` 都不写**（照抄）。
6. 控制器 `job.getMetadata().setManagedFields(null)`（`CicdController:57`）。
7. `getJobLog`：找不到 pod 时把 `null` 传给 `readNamespacedPodLog` ⇒ 异常被吞 ⇒ 返回 `null`
   （`CicdService:166-181`）。
8. `createTask` 的 `imageName` 校验 `^[a-zA-Z0-9-]+$`（下划线/点都不合法，`CicdService:282`）。
9. `g_ci_cd` 的字符串列入库前 `trim()`（厂商每个 setter 都 trim，`Cicd.java:34-106`）——
   本仓集中放在 `CicdRepository` 里做。
10. Job 报文常量（`CicdService:490`）：`ttlSecondsAfterFinished=12h`、`backoffLimit=0`、
    `restartPolicy=Never`、`imagePullPolicy=Always`、`command=["bash","/opt/build.sh"]`、
    nodeAffinity `[{cur-etcd Exists},{beta.kubernetes.io/arch In [amd64]}]`、
    toleration `control-plane Exists NoSchedule`、三个 hostPath 卷
    （`/var/run/docker.sock`、`nodeDir`、`/etc/localtime` type=File）、十个 env。

### 2.2 `/v1/pub`

11. `authing` 末尾的判断 `if (authingResultList != null || !authingResultList.isEmpty())` **`||` 恒真**
    ⇒ 重启段总是执行（`PublicController:1998`）；"服务在跑"的判定是**五个 replica 读数不全是 null**
    （`:2008`）。
12. `authing` 的 `upsert` 里 `serviceAuth1 != null` 是**多余判断**（`Optional` 永不为 null），
    实际语义是"存在就 update、否则 insert"（`AuthService:213-224`）；`result` 只在影响行数 == 1 时为 true。
13. `service_status` 的 `status` 取值里有厂商拼错的 **`STARING`**（`TenxUtils:70`、`:74`）。
14. `serviceStatus` 的 JSON 键是 **`appstatus`**（小写 s，`@JsonProperty`）而 `sysInfo` 没有覆盖；
    `sysInfo` 里**没有 `logging` 键**（厂商声明了 `loggingName` 却从未 `put`，`:2469-2470`）；
    `getSystemInfo` 命不中 pod 时 `status` 保持 **null**（`:2627-2635`）；
    `convertToPods` **无条件** add 一个 `{restartCount:0,state:null}`（`:2594-2607`）。
15. `patchResource`：Deployment 分支 `setRollingUpdate(..., maxSurge=0, maxUnavailable=1)`；
    StatefulSet 分支同样构造了 `maxSurge = 0` 但**从未使用**（死代码，`FileService:1218`）；
    两分支的**读取失败**语义不同（Deployment 回退、StatefulSet 直接 400），**patch 失败**都是
    `k8s.call.api.failed.to.change.deploy`。
16. `import`：`user.xls` 走 2 参重载 ⇒ `isImportFullBackUp=true`；`role.xls` 单参 ⇒ `isCover=false`；
    `sub_role.xls` 单参 ⇒ **`isCover=true`**（`SubRoleServiceImpl:535-536`，容易抄反）；
    `software.json`/`databaseUserRelation.json` 那两条恢复日志的 **level 写的是 `error`**；
    `fileAllToZip` 之间**不调 `closeEntry()`**；末尾 `else if (skip)` 里 `skip = false` 是死赋值。

## 3. 未做项与缺口（如实登记）

| 项 | 原因 | 处理 |
| --- | --- | --- |
| `software.json` → `SoftwareService.importSoftwareInfo:1195-…` | 该实现（几百行：Deployment 重建、镜像地址重写、节点绑定、白名单…）**尚未移植**，且属 `domain/software/**`，本批白名单不放行 | `IPubImportSupport.importSoftwareJson` 记 WARN + 写一条 **error 级恢复日志**后**跳过**，不伪造成功（与 `IBaseDataExportPort` 记录"尚未移植"的做法一致） |
| `databaseUser.json` → `DatabaseUserService.importDatabaseUser:94-…` | `g_database_user` 未建模；`DataBaseController.inputStreamToFile:526-530` 已有同款"只记日志跳过"先例 | 同上（与既有端点行为一致） |
| `user.xls` 的 `isImportFullBackUp=true` 语义 | 本仓 `IUserImportService.importUsers(rows, isCover)` **只有 2 参**，表达不了厂商的 `isImportFullBackUp`（它放宽两处 400：缺口令/缺昵称，并跳过关联写入） | 用 `isCover=true` 调用；差异记录在 `IPubImportSupport`/本文件；若后续要精确复刻需改 `domain/user/**` |
| `/topic/database` 进度推送 | 本平台 STOMP 只有 `/topic/software`、`/topic/k8s`、`userInfo` | 新端口 `IPubImportNotifyPort`，实现**只记日志**（C 类，同 `ISyncProgressNotifyPort` 的既有做法） |
| `StringEscapeUtils.unescapeJava` | 厂商读 `software.json`/`databaseUserRelation.json`/`databaseSql.json` 后都做一次 unescape；本仓 trigger 依赖里没有 commons-lang3，且既有 `DataBaseController` 对**同一个** `databaseUserRelation.json` 就是**不 unescape** | **主动偏离**：保持与既有端点一致（同一份导出包必须两边结果相同），不手写 unescape |
| `@AuditLog` 审计切面 | 本仓未实现审计切面 | 不写注解，响应契约不受影响 |

## 4. 类 C（本环境不可验证，代码照抄）

- `/cicd_v2`（K8s Job 创建：需 `ci-job-image` 镜像 + `cur-etcd` 节点标签）、`/cicdlog`（需真实 Job 的 pod）；
- `/cicdList` 的 job 状态回填（Docker Registry `getImageListByProject` + tag 列表，`findings/79`：registry 不可达）；
- `authing` 末尾的"重启已授权服务"（厂商走 TCE 开放 API；本仓改用 `batchRestartServices`）；
- `import` 的整站还原（真库/真集群写入）；
- `service_status`/`serviceStatus` 的实时状态读数；
- `patchResource`（写操作为了不碰真实工作负载，只做离线断言）。

## 5. 与厂商的两处主动偏离（B 类）

1. `fileAllToZip`/临时 zip/输出流用 try-with-resources 关闭（厂商泄漏句柄）；
2. `cicd_v2` 返回 `{}` 而不是 NPE：厂商在 Job 创建失败时返回 `new V1Job()`（metadata 为 null），
   随后 `getMetadata().setManagedFields(null)` 会 NPE ⇒ 500；本实现只在 metadata 非 null 时剥离
   `managedFields`。

## 6. 静态验证（本 agent 未跑 mvn）

用 JDK 21 + `javac` 对 `paas-core-{types,api,domain,infrastructure,trigger,case}` 的**全部 706 个源文件**
做了一次显式注解处理编译（`-processorpath lombok-1.18.46 -processor lombok...AnnotationProcessor`，
classpath 取本机 `~/.m2`）：

- 本批新增/改动的 **16 个文件全部通过**（仅 4 处 `unchecked` 警告，与 `ServiceRuntimeGateway:1026` 同风格）；
- 全仓剩余 4 处错误**都在其它 agent 正在写的文件里**，与本批无关：
  `domain/database/.../DatabaseUserServiceImpl.java:63,128`（符号未定义，疑似 `IDatabaseUserService` 尚未补方法）、
  `domain/node/.../ComputerResourceServiceImpl.java:39`（未实现 `nodeUsage()`）、
  `trigger/websocket/StompWebSocketConfig.java:22`（符号未定义）。

## 7. 交给 Lead 的两件事

1. **测试库 schema**（`paas-core-app/src/test/resources/test-schema.sql`，本批白名单不放行）需要补：

```sql
-- 厂商 gaop-9.sql:167-182 原样（列名/类型照抄）
CREATE TABLE IF NOT EXISTS g_ci_cd (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    user_id            VARCHAR(36)  DEFAULT NULL,
    image_name         VARCHAR(50)  DEFAULT NULL,
    image_tag          VARCHAR(50)  DEFAULT NULL,
    dockerfile_id      VARCHAR(50)  DEFAULT NULL,
    dockerfile_content VARCHAR(2000) DEFAULT NULL,
    buildfile_id       VARCHAR(50)  DEFAULT NULL,
    job_name           VARCHAR(50)  DEFAULT NULL,
    job_status         INT          DEFAULT NULL,
    description        VARCHAR(255) DEFAULT NULL,
    create_at          BIGINT       DEFAULT NULL,
    update_at          BIGINT       DEFAULT NULL,
    image_storage      VARCHAR(100) DEFAULT NULL
);

-- 既有 g_service_auth 缺三列（g_service_auth 的 upsert 会写 auth_code/created_at）
ALTER TABLE g_service_auth ADD COLUMN IF NOT EXISTS auth_code  CLOB;
ALTER TABLE g_service_auth ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE g_service_auth ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
```

2. `authing` 需要 `g_auth` 里有一行 `jar_name='default_auth'` 且 `public_key` 为 base64 X.509——否则
   端点按厂商契约返回 400 `auth.invalid.public.key.auth.code`（这是**正确行为**，不是缺陷）。
