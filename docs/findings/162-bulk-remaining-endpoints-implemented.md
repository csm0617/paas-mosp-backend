# 剩余端点一次性批量实现（第 166 轮）

按用户要求「把剩下的一次性全部实现了」，本轮用 5 个**写范围互不重叠**的并行 agent 把 127 条剩余端点
一次做完（口径：前端面 232 / 已实现 121 → **216** / 剩余 129 → **48**，扣掉 4 条口径噪声 ⇒ **真实剩余 44**）。

## 1. 分簇交付

| 簇 | 交付 | 说明 |
| --- | --- | --- |
| `/v1/pub` | 23/47 | 节点管理 7、集群/服务 2、计算机资源 3、服务器运维 4、杂项 5、VIP 2；未做 24（见 §3） |
| `/v1/backup` + `/v1/database` | 39/39 | 厂商两个 controller 的**全量**端点（各 16 / 23 条） |
| `/v1/image` | 16/18 | gaop-be 侧全量；`upload`/`clean` 属独立服务 `gaop-image-uploader`（18080） |
| `/v1/service` + `/v1/software` | 10/18 | 服务详情、updateServiceRole、imageStorage、logs、servicePods、schedulingNode、reStartBaseService、service_entry、reset_service、top |
| 零散 10 条 | 10/10 | CreateK8sServices/check、load/update、tenx_user、lang/import、config_map/importListen、mode/updateModeType、cs_software/upload、cs_software/{id}/relate、sync、sync/database/exist |

## 2. 统一验证（Lead 在合并后做）

| 项 | 结果 |
| --- | --- |
| `mvn -o test-compile` | **BUILD SUCCESS**（修掉 5 处：api 模块缺 jackson-annotations、DockerRegistryPort 重复方法、SyncCommandServiceImpl 两处 lambda 捕获非 final 变量、AuthInfoRepository id 类型 Integer/Long 不一致） |
| `mvn -o test` | **656 测试 0 失败**（上轮 602，+54 为各 agent 新增） |
| 应用启动 | **成功**，`/actuator/health` = UP，**零** `BuilderException`/`UnsatisfiedDependency`/`BeanCreation` 错误 |
| 路由冒烟 | 对新实现的 **154 条路径**逐条 GET：**0 条 404**（69×500 / 45×400 / 39×200 / 1×401） |
| 功能抽样 | `/v1/user/tenx_user`→`data:[]`（厂商 stub）、`/v1/image/imageLog`→`{list:[],total:0}`、`/v1/backup/backupSetting`→空列表、`/v1/backup/list`→真实种子行、`/v1/pub/getNodeAlertSetting`→三档阈值、`/v1/image/projectList`→既有 200 |

**500 的构成**（冒烟里 69 条）全部可解释，不是 wiring 缺陷：
`HttpRequestMethodNotSupportedException`（用 GET 打 POST/PUT/DELETE 端点，被全局处理器折成厂商同款
`code:-1`）、`MissingServletRequestParameterException`（同前，既有行为）、`FileNotFoundException`
（`/v1/backup/baseData` 的基础数据导出未移植）、`IllegalStateException`（`/v1/pub/vip` 的 vip daemon 不可达）。

## 3. 明确未做的 44 条（不是漏做）

| 原因 | 条数 | 明细 |
| --- | --- | --- |
| **属独立微服务**（不是 gaop-be） | 12 | `gaop-fdfs` 7（`file/{id}`/`delete`/`download`/`list`/`storage`/`upload`、`fdfs/upload`）、`FileProjectController` 2（需 `g_file_project`）、`gaop-image-uploader` 2（`image/upload`/`clean`）、口径噪声 4（`pods`/`persistentvolumes`/`mail/status`/`thirdPart`/`user/getLoginUser` 同源） |
| **需新数据源/新表** | 6 | Cicd 6 条（需 `g_cicd` 表） |
| **需 POI / 整包导入导出** | 9 | `/v1/backup` 的 `base`/`baseData`/`singleBaseData`/一键备份（user/role/sub_role/software 四类 xls 写手未移植）、`/v1/database` 的 `import`/`export`、`/v1/software` 的 `backup`/`v2/backup`/`import` |
| **外部依赖（类 C）** | 8 | `/v1/pub` 监控三条（Tenx Prometheus 范围查询框架 ~960 行）、`authing`/`import`/`service_status`/`serviceStatus`、`fdfs/status`、`patchResource` |
| **破坏性 / 待定边界** | 3 | `/v1/service/node`（删节点）、`/v1/service/patch`（依赖时速云 App）、`/v1/service/serviceInfoByNode`（需外部监控后端） |
| **厂商字节码反编译失败** | 1 | `/v1/database/backup_db_data_sync`（CFR 只留 `Decompilation failed`） |
| 其它 | 5 | `/v1/software/relationService`（裸路径：厂商后端**没有**这个映射，口径噪声）、`/v1/software/{id}/updateRemoteCs`（multipart+SSH，类 C）、`/v1/config_map/importListen` 的覆盖同步"先删"分支等 |

## 4. 类 C 清单（代码齐全、本环境不可验证）

- **SSH**：`/v1/pub` 节点管理 7 + `unify/etcd` + 服务器运维 4 + `nodeDisk` + `vip`、
  `/v1/service/imageStorage`/`schedulingNode`/`reStartBaseService`、`cs_software` 两条、
  软件 CS 分支启停、`/v1/database` 与 `/v1/backup` 的 mysqldump/mysql 子进程
- **registry/docker**：`/v1/image` 的 12 条（Harbor API / docker daemon unix socket）
- **Loki**：`/v1/service/logs`；**Prometheus/metrics-server**：`/v1/software/top`、pub 监控三条
- **对端 HTTP**：`/v1/sync`、`/v1/sync/database/exist`、`tenx_user`（厂商本身是 stub）、`mode` 的腾讯云数据源
- **无 Kafka/websocket 通道**：`/topic/sync`、`/topic/backup`、`/topic/database` 等进度推送只记日志

## 5. 新增依赖与配置（唯一两处 Lead 决策）

- `paas-core-api` 加 `com.fasterxml.jackson.core:jackson-annotations`（版本由 BOM 管理）——
  只为少数 DTO 上的 `@JsonInclude(NON_NULL)`（照抄厂商"子节点非空才序列化"的契约）。
- 未改 `application.yml`；所有新配置都用 `@Value("${...:<厂商默认值>}")`
  （`path.save`、`file.upload`、`gaop.ssh.*`、`gaop.harbor.url`、`gaop.docker.host`、
  `prometheus.*`、`mount.path`、`tenx.open.api.user-list-token` 等）。

## 6. 方法教训（写给下一轮）

1. **并行 agent 必须"不跑 mvn"**：5 个 agent 同时构建会争抢 `target/`，且各自看到的错误里混着别人的在制品。
   本轮统一由 Lead 在合并后编译，效果很好——**首个错误是 `DockerRegistryPort` 的重复方法**，
   它让 javac 提前失败、Lombok 处理器没跑完，于是**全模块 3000+ 条"找不到符号 log"的假错误**；
   修掉那一个方法后整树只剩 3 处真错。**遇到大面积"找不到符号"先找第一个硬错误**。
2. **文件白名单要连"基础设施端口实现文件"一起放行**：pub 与 backup 两个 agent 因为白名单没放行
   `infrastructure/adapter/port/*` 与 `gateway/*`，被迫把 HTTP/SSH 适配塞进 controller 或
   repository，事后需要 Lead 认可（本轮已接受这两个越界文件）。
3. 口径噪声要**按前缀**判（前端有、厂商后端同前缀下一条都没有），不能用"整条路径精确存在"判——
   两边的路径变量归一化粒度可能差一个 `/X`。
