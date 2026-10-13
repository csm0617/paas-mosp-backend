# 03 · 限界上下文切分证据（MSOP v1.3.4 / 内部代号 gaop）

> **本工单不做切分决定**。本文件只产出让切分**可决策**的证据：三方映射矩阵、耦合度量、候选上下文规模、不变量真伪、纯 CRUD 转发清单、反例讨论。
> 取证对象：`/Users/csm/Desktop/Project/msop`。
> 主证据源（**只读**，本工单未修改其中任何一个字节）：
> - `recovered/gaop-be/src/main/java/com/glriverside/gaop/be/**`（CFR 0.152 反编译产物，无注释）
> - `recovered/gaop-be/src/main/resources/db/migration/*.sql`（87 个 Flyway 迁移，厂商原文，未反编译）
> - `recovered-web/api-catalogue.json`（前端调用面，196 条）
> - `docs/findings/00..02`、`docs/MSOP-产品生命周期文档.md`（已有整理，本文件不重复其结论，只在其上补耦合证据）
> 取证动作：**纯离线文件读取 + 正则/语法级解析**。未连数据库、未联网、未 SSH 任何集群。
> 取证时间：2026-10-14

## 0. 记号与口径

### 0.1 路径缩写（本文件内统一使用）

| 缩写 | 展开 |
|---|---|
| `$BE/` | `recovered/gaop-be/src/main/java/com/glriverside/gaop/be/` |
| `$MIG/` | `recovered/gaop-be/src/main/resources/db/migration/` |
| `$WEB/` | `recovered-web/` |

引用格式：`〔源：$BE/service/UserService.java:276〕`。`$BE` 内的路径为**可直接 `sed -n '276p' $BE/service/UserService.java` 复查**的相对行号。

两条引用约定（避免歧义）：

1. **同一表格单元格或同一段落内，后续的裸 `:N` / `:N-M` 简写，指该格/该段内最近一次给出的完整路径位号。**
   例：`〔源：$BE/controller/UserController.java:378〕…；:420 …；:427 …` 中的 `:420`、`:427` 均指 `$BE/controller/UserController.java`。
2. **`〔源：A,B,C〕` 形式表示同一句结论由 A、B、C 三处**独立**证据支撑**，任一处可单独复查。

### 0.2 对象计数（本工单复算，与工单标题数字对齐）

| 口径 | 数量 | 依据 |
|---|---|---|
| 顶层 `controller/*.java` | **39** | `ls $BE/controller/*.java \| wc -l` |
| `controller/**` 内 `@RestController`/`@Controller` 类 | **52** | 含 `pub/`、`cloud/`、`webhook/`、`websocket/` 子包 |
| 端点（顶层 39 个 controller） | **370** | `grep -c` 逐个类复算一致 |
| 端点（全 `controller/**`） | **604** | 与 `docs/findings/02` 的「605 个端点」差 1，见 §7 |
| 顶层 `service/*.java` | **41** | 其中 6 个是接口，实现分居 `service/impl/` |
| service 单元（**每个类/接口独立成单元**，含子包 `@Service`） | **50** | 见下 |
| 其中：接口 | 5 | `AbstractImageService`、`LanguageService`、`ModeService`、`NodeVersionService`、`SubRoleService` |
| 其中：接口的实现类 | 6 | `K3sImageServiceImpl`、`K8sImageServiceImpl`、`LanguageServiceImpl`、`ModeServiceImpl`、`NodeVersionServiceImpl`、`SubRoleServiceImpl` |
| 其中：2 个第三方 API 客户端 | 2 | `DockerRegistryApiService`、`LokiApiService` |
| 顶层 `dao/*.java` | **79** = 39 个 `*DynamicSqlSupport` + 40 个 `*Mapper` | `$BE/dao/mysql/` 另有 2 个 |
| **逻辑 DAO 单元** | **40** | `XxxDynamicSqlSupport` 与 `XxxMapper` 是同一 DAO 的两半，必须合并计数（§1.1） |
| 迁移中的 `g_*` 表 | **41**（在用 39 + `g_resource` 已删 + `g_oauth_code` 无 Java 引用） | 见 `docs/findings/01` §1.1 |
| **有 DAO 映射的表** | **39** | §1.2 |
| 前端调用面条目 | **196**，可 100% 归属到 controller | §2.8 |

> **口径警告 1（DAO：79 → 40）**：工单标题里的「79 个 DAO」是按**文件**计数。按**逻辑单元**只有 40 个。本文件全部矩阵与度量都用 40 这个口径，否则会把同一个 DAO 数两遍、把耦合量算成 2 倍。这是本文件与工单标题唯一的口径差异。
>
> **口径警告 2（service：接口与实现不能合并）**：一个接口可以有**多个实现** —— `AbstractImageService` 有 `K3sImageServiceImpl` 与 `K8sImageServiceImpl` 两个实现，按集群类型在运行期选择。若把它们合并成一个 unit，会造出一个**幻影 unit，持有两个实现表的并集**。本文件的做法：**每个类独立成 unit**；仅当需要解析「接口类型字段」时才把该接口展开为其全部实现的并集，并在 §1.2 的「承载 service」列注明**具体类**。
> 这是取证过程中实际修正过的一个建模错误，特此留痕 —— 它本身就是一个领域事实（见 §6 K6 反例 2）。

### 0.3 度量方法的诚实声明（决定这些数字能/不能证明什么）

| 度量 | 方法 | 可信度 |
|---|---|---|
| 表 ↔ DAO | 精确：`*DynamicSqlSupport` 的 `super("g_x")` + `*Mapper` 内裸 SQL 的 `from/join/into/update` 目标 | **高**（字面证据） |
| service ↔ DAO/service | 精确：字段声明与构造器参数的类型 | **高**（声明即意图） |
| service ↔ 表 | 传递闭包（DAO + service 依赖） | 中（会把「调用了 A 服务」误算成「碰了 A 的全部表」） |
| controller ↔ 端点 | 精确：注解 + 类级 `@RequestMapping` | **高**（逐类 `grep -c` 已复核） |
| 端点 ↔ 表（**精确列**） | 把方法体内 `field.method(` 解析到 service 的**同名方法**，取该方法的**方法级**表集；解析不到的调用点退化为「该 service 全表集」并单独计数；接口类型字段展开为其全部实现 | 中高，**24 个调用点未解析**（§2.7） |
| 端点 ↔ 表（**上界列**） | 方法体内出现任一 service 字段名 → 计该 service 全表集 | 低（上界，仅用于量化「精确列的漏报风险」） |
| 同方法多表 | 顶格 4 空格缩进切分方法体，统计其内 DAO 字段引用 | 中（含少量字段声明与 setter 噪声，已单独给出「去噪」数） |

> 反编译产物**没有原厂注释**，本文件中凡属推断一律标 `【推断】`；凡在代码里读不到的，一律写 **未定论**。

---

## 1. 三方映射矩阵：表 → 拥有它的 DAO → 暴露它的端点

### 1.1 为什么必须先把 79 个 DAO 文件合并成 40 个逻辑 DAO

MyBatis Dynamic SQL 的产物是**一对文件**：`UserDynamicSqlSupport`（表与列元数据）+ `UserMapper`（CRUD 方法）。表名只写在 support 类里：

- 〔源：$BE/dao/UserDynamicSqlSupport.java:50〕 → `super("g_user")`
- 39 个 support 类各自给出 1 张表，**一一对应，无多表 support**。

把 79 个文件按类名基干合并后得到 **40** 个逻辑 DAO（39 个「support+Mapper」对，加 1 个无 support 的 `DataBaseManagerMapper`）。

### 1.2 矩阵（39 张表）

两列端点数的含义见 §0.3：

- **端点（精确）**：把 controller 方法体里 `field.method(...)` 解析到 service 的**同名方法**，只计该**方法自己**访问的表。为**下界偏保守**（24 个调用点无法解析时已退化为上界，见 §2.7）。
- **端点（上界）**：方法体内只要出现该 service 的字段名，就计满该 service 的全表集。**上界**；它与「精确」的差值 = 这张表的边界有多糊。

「承载它的 service」列**只列自己声明了该表 Mapper 的具体类**。接口不承载表 —— 因此 `LanguageService`（接口）不出现在 `g_language` 行，`LanguageServiceImpl` 才出现（原因见 §0.2 口径警告 2）。

| # | 表 | 拥有它的 DAO（逻辑单元） | 直接承载它的 service（具体类） | 端点（精确） | 端点（上界） | 候选 |
|---|---|---|---|---|---|---|
| 1 | `g_activate` | `ActivateMapper` | `AuthService`, `DynamicLoadingService`, `LicenceService` | 1 | 23 | K3 软件授权与激活 |
| 2 | `g_alert_record` | `AlertRecordMapper` | `AlertService` | 4 | 9 | K9 告警 |
| 3 | `g_alert_rule` | `AlertRuleMapper` | `AlertService` | 7 | 9 | K9 告警 |
| 4 | `g_alert_strategy` | `AlertStrategyMapper` | `AlertService` | 6 | 9 | K9 告警 |
| 5 | `g_auth` | `AuthInfoMapper` | `AuthService`, `DynamicLoadingService`, `LicenceService`, `SoftwareService` | 7 | 90 | K3 软件授权与激活 |
| 6 | `g_backup` | `BackupMapper` | `BackService`, `DatabaseService` | 5 | 41 | K5 备份恢复 |
| 7 | `g_backup_setting` | `BackupSettingMapper` | `BackService`, `DatabaseService` | 2 | 41 | K5 备份恢复 |
| 8 | `g_ci_cd` | `CicdMapper` | `CicdService` | 5 | 6 | K6 镜像与制品 |
| 9 | `g_client` | `ClientMapper` | `ClientService` | 5 | 5 | K12 客户端分发 |
| 10 | `g_database` | `DataBaseManagerMapper`, `DatabaseMapper` | `BackService`, `DatabaseService`, `DatabaseUserService` | 10 | 46 | K4 数据服务(DBaaS) |
| 11 | `g_database_exec_log` | `DatabaseExecLogMapper` | `DatabaseService` | 0 | 25 | K4 数据服务(DBaaS) |
| 12 | `g_database_record` | `DatabaseRecordMapper` | `DatabaseRecordService`, `DatabaseService` | 0 | 25 | K4 数据服务(DBaaS) |
| 13 | `g_database_user` | `DatabaseUserMapper` | `DatabaseService`, `DatabaseUserService` | 7 | 30 | K4 数据服务(DBaaS) |
| 14 | `g_dictionary` | `DictionaryMapper` | `UserAuditService` | 1 | 2 | K8 平台设置与国际化 |
| 15 | `g_file` | `FileMapper` | `CicdService`, `DatabaseService`, `FileService` | 16 | 52 | K7 文件与仓库 |
| 16 | `g_file_project` | `FileMapper`, `FileProjectMapper` | `CicdService`, `DatabaseService`, `FileProjectService`, `FileService` | 19 | 55 | K7 文件与仓库 |
| 17 | `g_group` | `ImageGroupMapper` | `K3sImageServiceImpl` | 4 | 17 | K6 镜像与制品 |
| 18 | `g_image_log` | `ImageLogMapper` | `DockerService`, `ImageService`, `K3sImageServiceImpl`, `K8sImageServiceImpl` | 2 | 20 | K6 镜像与制品 |
| 19 | `g_language` | `LanguageMapper` | `LanguageServiceImpl` | 1 | 8 | K8 平台设置与国际化 |
| 20 | `g_language_version` | `LanguageVersionMapper` | `LanguageServiceImpl` | 7 | 8 | K8 平台设置与国际化 |
| 21 | `g_mode` | `ModeMapper` | `ModeServiceImpl` | 4 | 4 | K10 集群运维 |
| 22 | `g_pod_event` | `PodEventMapper` | —（无 service 层，controller 直连） | 1 | 1 | K2 软件目录与发布 |
| 23 | `g_role` | `RoleMapper`, `SoftwareMapper`, `UserMapper`, `UserRoleMapper` | `CsSoftwareUploadService`, `RoleService`, `SettingService`, `SoftwareService`, `SubRoleServiceImpl`, `UserAuditService`, `UserService` | 89 | 156 | K1 身份与战位 |
| 24 | `g_role_software` | `RoleSoftwareMapper`, `SoftwareMapper` | `CsSoftwareUploadService`, `RoleService`, `SettingService`, `SoftwareService`, `UserService` | 55 | 144 | K2 软件目录与发布 |
| 25 | `g_service` | `ServiceManagementMapper` | —（无 service 层，controller 直连） | 5 | 5 | **未归属** |
| 26 | `g_service_auth` | `ServiceAuthMapper` | `AuthService`, `DynamicLoadingService`, `LicenceService`, `SoftwareService` | 14 | 90 | K3 软件授权与激活 |
| 27 | `g_service_port` | `ServicePortMapper`, `SoftwareMapper` | `CsSoftwareUploadService`, `RoleService`, `SettingService`, `SoftwareService`, `UserService` | 59 | 144 | K2 软件目录与发布 |
| 28 | `g_setting` | `SettingMapper` | `FileService`, `K3sImageServiceImpl`, `LanguageServiceImpl`, `LicenceService`, `SettingService`, `UserService` | 20 | 109 | K8 平台设置与国际化 |
| 29 | `g_software` | `SoftwareMapper`, `UploadStoreRecordMapper` | `CsSoftwareUploadService`, `RoleService`, `SettingService`, `SoftwareService`, `StoreMangeService`, `UserService` | 53 | 147 | K2 软件目录与发布 |
| 30 | `g_software_app` | `SoftwareAppMapper`, `SoftwareMapper` | `CsSoftwareUploadService`, `RoleService`, `SettingService`, `SoftwareService`, `UserService` | 55 | 144 | K2 软件目录与发布 |
| 31 | `g_sub_role` | `SubRoleMapper`, `UserSubRoleMapper` | `RoleService`, `SubRoleServiceImpl`, `UserService` | 17 | 73 | K1 身份与战位 |
| 32 | `g_sub_role_role` | `SubRoleRoleMapper` | `RoleService`, `SubRoleServiceImpl` | 6 | 37 | K1 身份与战位 |
| 33 | `g_upload_store_record` | `SoftwareMapper`, `UploadStoreRecordMapper` | `CsSoftwareUploadService`, `RoleService`, `SettingService`, `SoftwareService`, `StoreMangeService`, `UserService` | 53 | 147 | K2 软件目录与发布 |
| 34 | `g_user` | `RoleMapper`, `UserMapper`, `UserRoleMapper`, `UserSubRoleMapper` | `RoleService`, `SoftwareService`, `SubRoleServiceImpl`, `UserAuditService`, `UserService` | 66 | 141 | K1 身份与战位 |
| 35 | `g_user_audit` | `UserAuditMapper` | `RoleService`, `UserAuditService`, `UserService` | 4 | 64 | **未归属** |
| 36 | `g_user_login` | `UserLoginMapper` | `RoleService` | 2 | 26 | K1 身份与战位 |
| 37 | `g_user_role` | `RoleMapper`, `UserMapper`, `UserRoleMapper` | `RoleService`, `SoftwareService`, `SubRoleServiceImpl`, `UserAuditService`, `UserService` | 64 | 141 | K1 身份与战位 |
| 38 | `g_user_sub_role` | `UserSubRoleMapper` | `SubRoleServiceImpl`, `UserService` | 8 | 53 | K1 身份与战位 |
| 39 | `g_white_list` | `WhiteListMapper` | `SoftwareService`, `WhiteListService` | 3 | 70 | K11 白名单 |

> **可复现**：上表两列端点数的全部数字，就是附录 A 脚本 `== 表 | DAO | 承载 service（具体类） | 端点(精确) | 端点(上界) ==` 一节的输出，逐字一致。

**矩阵本身已经把切分难点暴露出来**：`g_role` 精确 89 / 上界 156，差 67；K3 的 `g_activate` 精确 1 / 上界 23，差 22。差值越大 = 这张表被越多与它无关的端点顺带带过 = 边界越糊。

### 1.3 没有任何端点的表

39 张表里**只有 1 张**精确端点数为 0：

| 表 | 谁写 | 端点（精确/上界） | 证据 |
|---|---|---|---|
| `g_database_exec_log` | `DatabaseService` 内部 SQL 执行留痕 | **0 / 25** | 〔源：$BE/service/DatabaseService.java:446〕（`database.get.sql.error.log.exception`）；表由 `DatabaseExecLogDynamicSqlSupport`〔源：$BE/dao/DatabaseExecLogDynamicSqlSupport.java:33〕映射 |

〔推断〕它是 K4 的**内部技术表**，不构成独立上下文，应随宿主域迁移。

> **一个被这次建模修正纠正的误判**：`g_group`（`ImageGroupDynamicSqlSupport` → `super("g_group")`，〔源：$BE/dao/ImageGroupDynamicSqlSupport.java:23〕）在修正前的模型里被记成「0 端点」，因为它的唯一承载者 `K3sImageServiceImpl` 被错误地并进了接口 `AbstractImageService`（§0.2 口径警告 2）。修正后 `g_group` 精确 **4** / 上界 17，承载者是**具体类** `K3sImageServiceImpl`（`imageGroupMapper` 字段〔源：$BE/service/impl/K3sImageServiceImpl.java:100〕，读写点 :176,:183,:188,:195,:196,:215,:491,:508,:715,:717,:726,:731）。**教训：把接口与实现合并计数，会同时算错「谁承载表」与「表有多少端点」两件事。**

### 1.4 前端调用面核对

`$WEB/api-catalogue.json` 有 **196** 条 `{method,url}`。用「端点路径模板 → 实际 controller」匹配（`{}` 归一化变量段）：

- **196 / 196 命中**，无一条无主。
- 归属最重的 controller：`ServiceController` 31、`TenxApiV2Controller` 15、`ImageController` 13、`PublicController` 12、`SoftwareController` 12、`UserController` 10、`BackController` 10、`LicenceController` 8。
- **同一 `(verb, url)` 被两个 controller 处理的条目数 = 0**，即前端调用面在路由层没有二义。

> 结论：**前端调用面可以 100% 归因到 controller**，因此「以端点划分上下文」在证据上是可行的。但注意 §3 的 K2 —— 端点归因清楚，**不代表**它背后的 service 边界清楚。

---

## 2. 耦合度量

### 2.1 度量脚本

全部数字由附录 A 的脚本产出。最小复现（不依赖任何仓库外文件）：

```bash
cd /Users/csm/Desktop/Project/msop
BE=recovered/gaop-be/src/main/java/com/glriverside/gaop/be

# 表 -> DAO（唯一权威来源：support 类的 super("g_x")）
for f in $BE/dao/*DynamicSqlSupport.java; do
  printf '%s -> %s\n' "$(basename "$f" .java)" "$(grep -o 'super("g_[a-z_]*")' "$f")"
done | sort

# 逻辑 DAO 数（79 文件 → 40 单元）
ls $BE/dao/*DynamicSqlSupport.java | wc -l   # 39
ls $BE/dao/*Mapper.java            | wc -l   # 40
# 合并规则：XxxDynamicSqlSupport 与 XxxMapper 同名基干视为一个逻辑 DAO

# 端点总数（全 controller 包）
grep -rhoE '@(Get|Post|Put|Delete|Patch)Mapping' $BE/controller/ | wc -l   # 604

# controller 直连 DAO（绕过 service 层）
grep -rn '^\s*private [A-Za-z]*Mapper [a-z]' --include=*.java $BE/controller/ | grep -v ObjectMapper

# service -> service 注入
grep -rnE '^\s*private [A-Z][A-Za-z0-9_]*(Service|ServiceImpl) ' --include=*.java $BE/service/

# 数据库级唯一约束（全仓唯 3 条）
grep -rn 'UNIQUE' $MIG/*.sql
```

完整解析脚本见 **附录 A**（单文件、无第三方依赖，`python3 msop_ctx.py <repo>` 即可重放 §1.2 矩阵与 §2.4/2.6 的计数）。

### 2.2 DAO 层的跨表：40 个 DAO 里有 6 个自己就是跨表的

| DAO | 覆盖的表 | 证据 |
|---|---|---|
| `SoftwareMapper` | `g_software`, `g_software_app`, `g_role_software`, `g_service_port`, `g_upload_store_record`, `g_role` | 〔源：$BE/dao/SoftwareMapper.java:132〕 裸 SQL：`select app_id from g_role_software rs INNER JOIN g_role r on rs.role_id=r.id` |
| `RoleMapper` | `g_role`, `g_user`, `g_user_role` | `$BE/dao/RoleMapper.java`（含 `g_user`/`g_user_role` 的裸 SQL） |
| `UserRoleMapper` | `g_role`, `g_user`, `g_user_role` | 同上 |
| `UserSubRoleMapper` | `g_sub_role`, `g_user`, `g_user_sub_role` | 同上 |
| `UploadStoreRecordMapper` | `g_software`, `g_upload_store_record` | 同上 |
| `FileMapper` | `g_file`, `g_file_project` | 同上 |

**`SoftwareMapper` 是全局最重的越界者**：它一张表的 Mapper 里直接 `INNER JOIN g_role`。这是 K2（软件）与 K1（战位）之间**编译期就存在的硬耦合**，不可能靠"换目录"消除，只能靠拆 DAO 或改用只读投影。

> 另：`DataBaseManagerMapper` 与 `MysqlMapper` 是**手写裸 SQL Mapper**（无 support 类），`g_database` 因此有两个 DAO 同时拥有它，两个都要迁。

### 2.3 表的「多主」程度：谁的表是好几个人在写

按**直接承载该表的具体 service 类**数排序（§1.2 第 4 列）：

| 扇出 | 表 | 具体承载类 |
|---|---|---|
| **7** | `g_role` | `RoleService`, `UserService`, `SubRoleServiceImpl`, `UserAuditService`, `SoftwareService`, `SettingService`, `CsSoftwareUploadService` |
| **6** | `g_software` | `SoftwareService`, `RoleService`, `UserService`, `SettingService`, `StoreMangeService`, `CsSoftwareUploadService` |
| **6** | `g_upload_store_record` | 同上 6 个 |
| **6** | `g_setting` | `SettingService`, `UserService`, `LicenceService`, `FileService`, `LanguageServiceImpl`, `K3sImageServiceImpl` |
| **5** | `g_role_software` | `SoftwareService`, `RoleService`, `UserService`, `SettingService`, `CsSoftwareUploadService` |
| **5** | `g_service_port` | 同上 5 个 |
| **5** | `g_software_app` | 同上 5 个 |
| **5** | `g_user` / `g_user_role` | `UserService`, `RoleService`, `SoftwareService`, `SubRoleServiceImpl`, `UserAuditService` |
| **4** | `g_auth` / `g_service_auth` | `AuthService`, `LicenceService`, `DynamicLoadingService`, `SoftwareService` |
| **4** | `g_file_project` | `FileService`, `FileProjectService`, `DatabaseService`, `CicdService` |
| **3** | `g_backup`, `g_backup_setting` | `BackService`, `DatabaseService` |
| **3** | `g_database` | `DatabaseService`, `DatabaseUserService`, `BackService` |
| **3** | `g_file` | `FileService`, `DatabaseService`, `CicdService` |

**`g_role` 被 7 个具体 service 类直接持有**是全域最高的，且这 7 个分属两个候选：K1 的 `RoleService`/`UserService`/`SubRoleServiceImpl`/`UserAuditService`，与 K2 的 `SoftwareService`/`SettingService`/`CsSoftwareUploadService`。这是切分时**必须先解决**的一张表。

**`g_setting` 的 6 个承载类横跨 K1/K3/K6/K7/K8 五个候选** —— 它是全域最"公共"的表，比 `g_role` 更分散。

### 2.4 service → service 注入：131 条边，其中 **33 条跨候选**

按候选聚合（跨候选边，出边计一次）：

| 出 → 入 | 跨候选边数 | 明细（逐条，非代表性） |
|---|---|---|
| K5 → K1 | 3 | `BackService→RoleService`, `BackService→SubRoleService`, `BackService→UserService` |
| K1 → K5 | 3 | `RoleService→BackupLogService`, `SubRoleService→BackupLogService`, `UserService→BackupLogService` |
| K2 → K6 | 3 | `SoftwareService→DockerService`, `SoftwareService→ImageService`, `SoftwareService→NodeVersionService` |
| K5 → K2 | 2 | `BackService→AppService`, `BackService→SoftwareService`〔推断：`AppService` 计入 K2〕 |
| K5 → K4 | 2 | `BackService→DatabaseService`, `BackService→DatabaseUserService` |
| K5 → K6 | 2 | `BackService→ImageService`, `BackService→NodeVersionService` |
| K10 → K2 | 2 | `ComputerService→SettingService`, `ModeService→SoftwareService`〔推断：`SettingService` 计入 K2〕 |
| K10 → K6 | 2 | `DashboardsService→AbstractImageService`, `DashboardsService→NodeVersionService` |
| K2 → K4 | 2 | `SoftwareService→DatabaseService`, `SoftwareService→DatabaseUserService` |
| 其余 12 条 | 各 1 | `K5→K8`(LanguageService)、`K5→K11`(WhiteListService)、`K6→K7`(FileService)、`K4→K5`(BackupLogService)、`K4→K7`(FileService)、`K6→K1`(UserService)、`K10→K3`(AuthService)、`K2→K5`、`K2→K3`(LicenceService)、`K2→K11`(WhiteListService)、`K1→K2`(SettingService)、`K1→K8`(UserAuditService) |

跨候选出度排序：**K5 备份恢复 11 > K2 软件目录与发布 8 > K10 集群运维 5 = K1 身份与战位 5 > K6 镜像与制品 2 = K4 数据服务 2**。

**`BackService`（K5 备份恢复）是全域出度最大的 service** —— 声明注入 **12 个 service**（其中 **11 条为跨候选边**），同时覆盖 K1/K2/K4/K6/K8/K11：

`〔源：$BE/service/BackService.java〕` 的字段区含 `AppService, BackupLogService, DatabaseService, DatabaseUserService, ImageService, LanguageService, NodeVersionService, RoleService, SoftwareService, SubRoleService, UserService, WhiteListService` 共 12 个 service 类型。

【推断】`BackService` 的语义是「**横切所有域的全量备份**」，它天然不可能是某个域的内部实现——这是切分时要么承认「备份是横切关注点」，要么引入「各域自己导出/导入」的反转接口。**本工单不裁决**。

### 2.5 方法级多表访问：同一个方法里碰 ≥2 张表

剔除字段声明与 setter 噪声后，全仓 **203 个方法**在**同一个方法体内**直接访问 ≥2 张表。按类分布：

| 方法数 | service | 说明 |
|---|---|---|
| 48 | `SoftwareService`（217 方法） | 全域最重 |
| 47 | `UserService`（105 方法） | **命中率 45%**，几乎每两个方法就跨表 |
| 32 | `RoleService`（64 方法） | 命中率 50% |
| 17 | `SubRoleServiceImpl`（45 方法） | |
| 13 | `CsSoftwareUploadService`（34 方法） | |
| 13 | `FileService`（72 方法） | |
| 9 | `StoreMangeService`、7 `AlertService`、5 `DatabaseService`、3 `UserAuditService` | |

**单方法内跨 6 表的硬证据**：〔源：$BE/service/RoleService.java:708-725〕 的 `deleteRoleRelation` 在**一个 `@Transactional` 方法**内顺序删 `g_sub_role_role`、`g_user_audit`、`g_user_role`、`g_user_login`、`g_role_software`、`g_role`：

```
@Transactional                            :707
subRoleRoleMapper.delete(...)             :720
userAuditMapper.delete(...)               :721
userRoleMapper.delete(...)                :722
userLoginMapper.delete(...)               :723
roleResourceMapper.delete(...)            :724
roleMapper.deleteByPrimaryKey(...)        :725
```

这 6 张表分属 K1（`g_role`,`g_user_role`,`g_user_login`,`g_sub_role_role`）、K2（`g_role_software`）、K8（`g_user_audit`）。**这是「战位删除」这一个业务动作必须原子完成的跨候选事务** —— 若按候选拆成独立服务，这个事务就要跨服务，必须显式设计编排或最终一致。这是切分的硬约束之一。

### 2.6 controller 跨域调用与「绕过 service 层」

**(a) controller → service 的跨候选调用**：**40 条**，其中 **K2 出边占 32 条**（K2→K6 8、K2→K4 6、K2→K1 6、K2→K3 5、K2→K10 4、K2→K7 3）。`K2→K6` 从 6 升到 8，是因为把 `AbstractImageService` 展开到两个实现后，K2 的 controller 对镜像域的实现类依赖被如实计入。`PublicController`（3782 行、68 端点）与 `ServiceController`（4108 行、51 端点）两个类就贡献了主要部分。

**(b) controller 直接持有 Mapper（完全绕过 service 层）共 8 处**：

| controller | 直连的 Mapper | 证据 |
|---|---|---|
| `ServiceController` | `ServiceManagementMapper`, `SoftwareMapper`, `SoftwareAppMapper`, `ServicePortMapper`, `ServiceAuthMapper`, `PodEventMapper` | 〔源：$BE/controller/ServiceController.java:250,262,264,266,268,274〕 |
| `DataBaseController` | `SoftwareMapper`, `ServiceAuthMapper`, `MysqlMapper` | 〔源：$BE/controller/DataBaseController.java:106,108,114〕 |
| `PublicController` | `ServiceManagementMapper`, `AuthInfoMapper`, `RoleMapper` | 〔源：$BE/controller/pub/PublicController.java:331,345,353〕 |
| `TestController` | `ModeMapper`, `SubRoleMapper`, `RoleMapper` | 〔源：$BE/controller/TestController.java:29,31,33〕 |
| `SyncController` | `SoftwareMapper` | 〔源：$BE/controller/SyncController.java:171〕 |
| `ImageController` | `ImageLogMapper` | 〔源：$BE/controller/ImageController.java:80〕 |
| `AuthController` | `AuthInfoMapper` | 〔源：$BE/controller/AuthController.java:99〕 |
| `TenxApiV2Controller` | `AlertStrategyMapper`（**运行时从容器捞**） | 〔源：$BE/controller/cloud/TenxApiV2Controller.java:210〕：`AlertStrategyMapper alertStrategyMapper = SpringBeanUtil.getBean(AlertStrategyMapper.class);` |

另有 〔源：$BE/controller/Job/BackUpJob.java:44〕：`private BackupMapper backupMapper = SpringBeanUtil.getBean(BackupMapper.class);` —— **Quartz Job 被放在 controller 包里，且服务定位器式取 DAO**。

**这张表的含义**：`g_service`（`ServiceManagement`）与 `g_pod_event` 两张表**根本没有 service 层**，只被 controller 直写（§1.2 第 22、25 行。注意：`g_pod_event` 另有启动组件写入者 〔源：$BE/ApplicationStarted.java:66,353-363〕）。它们不属于任何现有 service 单元，是切分时的**孤儿表**。

### 2.7 端点 → 表的解析质量（精确列的可信度）

用附录 A 的方法解析 604 个端点后：

- 解析成功并落到具体 service 方法的调用点：绝大多数。
- **未解析的调用点：24 个**（多为经 `RunSqlScript`/反射/`SpringBeanUtil.getBean` 发起的调用）。这 24 个已退化为「该 service 全表集」（上界），是精确列的主要漏报来源。
- 只有 **13 个端点**是通过 controller 直连 DAO 命中表的（与 §2.6 的 8 处直连对应）。

**604 个端点里只有 221 个（36.6%）触达任何数据库表**；其余 **383 个（63.4%）完全不碰表**，是纯 k8s / 时速云 / Prometheus / Loki / Docker / Harbor 编排或纯文件流。这个比例本身就是最重要的切分证据之一：**MSOP 后端的主体不是数据应用，而是编排代理。**

### 2.8 度量偏差声明（这些数字**不能**证明什么）

1. **service → 表的传递闭包会放大耦合**。`SoftwareService` 注入 10 个 service，因此"SoftwareService 涉及 11 张表"包含其下游的表。§1.2 的「上界」列就是这个放大效应的直接体现，与「精确」列的差值不应被当作真实耦合。
2. **方法切分靠缩进**。CFR 产物中字段声明与被反编译成顶格方法的 lambda 会混入。§2.5 的 203 已是剔除 setter/字段噪声后的数。
3. **`SpringBeanUtil.getBean()` 式依赖不可静态解析**，只覆盖到 2 处（§2.6）。
4. **AOP 与定时任务是隐藏的数据写入者**，不在 service 依赖图里：〔源：$BE/aspect/AuditAnnotationAspect.java:70〕 持有 `UserAuditMapper`，19 个 controller 打了 `@AuditLog`，这批写入全部发生在 K1 之外的横切路径上；〔源：$BE/ApplicationStarted.java:66〕 持有 `PodEventMapper` 在启动时写 `g_pod_event`；`$BE/job/SystemMonitorJob.java` 同理。

---

## 3. 候选上下文：规模与职责

> 12 个候选 + 2 张未归属表。**规模数字**：表 / 逻辑 DAO / service 单元 / controller 类 / 端点 / **触库端点** / service 代码行 / controller 代码行 / DAO 代码行。
> 同一类可出现在两个候选（如 `TenxApiV2Controller` 同时是 K9 与 K10 的端点载体），**跨候选的类已在下表用 * 标出**。
>
> **「触库端点」是本表最该被切分者注意的一列**：它把"这个候选有多少 HTTP 面"与"它真正有多少持久化行为"分开。K10 是 2%（105 个端点只碰 1 张 `g_mode` 表），K9 是 17%（52 个端点里 43 个是运维转发）。**端点多的域不一定是领域，可能只是适配器。**

| 候选 | 表 | DAO | svc 单元 | ctrl | 端点 | 触库端点 | svc 行 | ctrl 行 | DAO 行 |
|---|---|---|---|---|---|---|---|---|---|
| **K1** 身份与战位 | 7 | 8 | 4 | 5 | 63 | 48 (76%) | 4254 | 1307 | 1112 |
| **K2** 软件目录与发布 | 6 | 6 | 5 | 13 | 229 | 80 (35%) | 6726 | 11481 | 832 |
| **K3** 软件授权与激活 | 3 | 3 | 3 | 3 | 21 | 14 (67%) | 1918 | 525 | 377 |
| **K4** 数据服务（DBaaS） | 4 | 5 | 3 | 3 | 29 | 14 (48%) | 2841 | 738 | 565 |
| **K5** 备份恢复 | 2 | 2 | 2 | 2 | 20 | 8 (40%) | 1912 | 252 | 248 |
| **K6** 镜像与制品 | 3 | 3 | 8 | 4 | 26 | 12 (46%) | 2921 | 476 | 389 |
| **K7** 文件与仓库 | 2 | 2 | 2 | 2 | 25 | 14 (56%) | 1653 | 302 | 292 |
| **K8** 平台设置与国际化 | 4 | 4 | 4 | 5 | 22 | 13 (59%) | 878 | 325 | 531 |
| **K9** 告警 | 3 | 3 | 1 | 1* | 52 | 9 (17%) | 580 | 638* | 372 |
| **K10** 集群运维 | 1 | 1 | 10 | 10 | 105 | **2 (2%)** | 7004 | 1825 | 124 |
| **K11** 白名单 | 1 | 1 | 1 | 1 | 3 | 2 (67%) | 109 | 67 | 124 |
| **K12** 客户端分发 | 1 | 1 | 1 | 1 | 6 | 5 (83%) | 188 | 90 | 163 |
| **未归属**（`AppController`+`WebhookController`） | 2 | 1 | — | 2 | 3 | 0 | — | — | — |

### K1 · 身份与战位（Identity & Position）

- **表**：`g_user`, `g_user_role`, `g_role`, `g_user_login`, `g_sub_role`, `g_user_sub_role`, `g_sub_role_role`
- **DAO**：`UserMapper`, `UserRoleMapper`, `RoleMapper`, `UserLoginMapper`, `SubRoleMapper`, `SubRoleRoleMapper`, `UserSubRoleMapper`, `SoftwareMapper`（越界，见 §2.2）
- **service**：`UserService`(2285 行/105 方法)、`RoleService`(1228 行/64 方法)、`SubRoleService`(接口, 70 行)、`SubRoleServiceImpl`(671 行/45 方法) → 合计 4254 行
- **触库端点**：48 / 63（76%），**是本表占比最高的候选** —— 身份域几乎每个端点都落库
- **controller**：`UserController`(25 端点)、`RoleController`(27)、`SubRoleController`(10)、`RegisterController`(1)、`WebsocketController`(0 端点)
- **职责一句话**：账号、战位（`g_role.type` 区分战位/角色）、子角色与三张关系表；登录会话与登录状态推送。

### K2 · 软件目录与发布（Software Catalog & Service Release）

- **表**：`g_software`, `g_software_app`, `g_role_software`, `g_service_port`, `g_upload_store_record`, `g_pod_event`
- **DAO**：`SoftwareMapper`, `SoftwareAppMapper`, `RoleSoftwareMapper`, `ServicePortMapper`, `UploadStoreRecordMapper`, `PodEventMapper`（孤儿），另 `ServiceManagementMapper` 未归属
- **service**：`SoftwareService`(**5050 行 / 217 方法**，全域最大)、`CsSoftwareUploadService`(664)、`StoreMangeService`(105)、`AppService`(606)、`SettingService`(301)
- **controller**：`SoftwareController`(44)、`ServiceController`(**4108 行 / 51**)、`CsSoftwareController`(8)、`StoreMangeController`(2)、`ConfigMapController`(12)、`SyncController`(28, 1370 行)、`InstallController`(2)、`ComponentController`(1)、`CreateK8sServicesController`(1)、`PublicController`(**3782 行 / 68**)、`PublicNodeManagerController`(7)、`SoftwareBackupV3Controller`(3)、`SoftwareImportV3Controller`(2) —— 共 **13 个 controller / 229 端点**
- **职责一句话**：软件（应用）目录、应用→服务→端口映射、战位可用软件清单、上传/仓库记录、导入导出与跨集群同步。

### K3 · 软件授权与激活（Licensing & Activation）

- **表**：`g_auth`, `g_activate`, `g_service_auth`
- **service**：`AuthService`(418)、`LicenceService`(1237 行/46 方法)、`DynamicLoadingService`(263)
- **controller**：`AuthController`(7)、`LicenceController`(11)、`DynamicLoadingController`(3)
- **职责一句话**：软件级授权码（JWT）签发与校验、机器码（含 GIS/时速云码）绑定、过期判定、框架激活公私钥管理、动态 jar 注入授权。

### K4 · 数据服务 DBaaS（Database as a Service）

- **表**：`g_database`, `g_database_user`, `g_database_record`, `g_database_exec_log`
- **DAO**：`DatabaseMapper`, `DataBaseManagerMapper`, `DatabaseUserMapper`, `DatabaseRecordMapper`, `DatabaseExecLogMapper`
- **service**：`DatabaseService`(2519 行/101 方法)、`DatabaseUserService`(267)、`DatabaseRecordService`(55)
- **controller**：`DataBaseController`(23)、`DatabaseUserController`(5)、`DatabaseRecordController`(1)
- **职责一句话**：为软件分配业务库与数据库账号、执行 SQL 文件、记录 SQL 执行日志与导入进度。

### K5 · 备份恢复（Backup & Restore）

- **表**：`g_backup`, `g_backup_setting`
- **service**：`BackService`(1912 行/67 方法)、`BackupLogService`(无表，纯 WebSocket 推送进度)
- **controller**：`BackController`(16)、`TestController`(4)
- **职责一句话**：平台级全量备份/恢复任务的 Quartz 编排，跨用户、软件、数据库、镜像、配置、负载均衡六类数据。

### K6 · 镜像与制品（Image & CI/CD）

- **表**：`g_image_log`, `g_group`, `g_ci_cd`
- **service**：`ImageService`(741)、`AbstractImageService`(**接口**, 293 行声明式)、`DockerService`(391)、`CicdService`(535)、`K3sImageServiceImpl`(806)、`K8sImageServiceImpl`(293)、`NodeVersionService`(接口, 15)、`NodeVersionServiceImpl`(85) → 合计 2921 行
- **⚠️ 一个接口两个实现**：`AbstractImageService` 由 `K3sImageServiceImpl`（K3s 集群）与 `K8sImageServiceImpl`（K8s 集群）实现，**只有 K3sImageServiceImpl 持有 `ImageGroupMapper`（`g_group`）**。见 §0.2 口径警告 2 与 §6 K6 反例 2
- **controller**：`ImageController`(17)、`CicdController`(7)、`RegistryWebhookController`(1)、`ImageUploadWebhookController`(1)
- **职责一句话**：镜像上传/推送/删除仓库分组、镜像上传日志状态、CI/CD 自动构建任务与 webhook 回执。

### K7 · 文件与仓库（File & Artifact Store）

- **表**：`g_file`, `g_file_project`
- **service**：`FileService`(1576 行/72 方法)、`FileProjectService`(77)
- **controller**：`PublicFdfsController`(21)、`FileProjectController`(4)
- **职责一句话**：FastDFS 文件元数据、业务软件（文件项目）分组、上传下载续传与 CRC 校验。

### K8 · 平台设置与国际化（Settings, i18n & Audit Dictionary）

- **表**：`g_setting`, `g_language`, `g_language_version`, `g_dictionary`
- **service**：`SettingService`(301)、`ExSettingService`(43，无表，动态 jar/bean 热插拔)、`LanguageService`(接口, 28)、`LanguageServiceImpl`(525)、`UserAuditService`(282) → 合计 878 行
- **controller**：`GlobalSettingController`(6)、`ExSettingController`(4)、`LanguageController`(7)、`PubLanguageController`(2)、`UserAuditController`(3)
- **职责一句话**：全局 KV 配置、国际化词条与语言版本、审计字典与审计流水查询。
- ⚠️ `g_user_audit` 被本工单列为**未归属**（§3.13），它与 K8 的 `g_dictionary` 同属 `UserAuditService`，与 K1 的 `g_user`/`g_role` 也同属该 service。**这张表是 K1/K8 的接缝**。

### K9 · 告警（Alerting）

- **表**：`g_alert_strategy`, `g_alert_rule`, `g_alert_record`
- **service**：`AlertService`(580 行/26 方法)
- **controller**：无独立 controller，**端点寄生在 `TenxApiV2Controller` 的 `/cloud/alert*` 前缀下**（9 个端点）
- **职责一句话**：接收 Loki 告警 webhook、按 fingerprint 去重、策略/规则/记录三层维护、启停同步到 Loki。
- **规模异常**：`g_alert_*` 三张表的精确端点数分别为 6/7/4，但 `TenxApiV2Controller` 共有 52 个端点——说明**告警的端点在物理上被塞进了一个运维 controller**，这是切分时的现实障碍。

### K10 · 集群运维（Cluster Ops / k8s Orchestration）

- **表**：`g_mode`（**唯一 1 张**）
- **service**：`TenxService`(3072 行/94 方法)、`K8sService`(81)、`InstancesService`(562)、`ComputerService`(1853 行/57 方法)、`DashboardsService`(961)、`ComponentService`(35)、`ModeService`(接口, 20)、`ModeServiceImpl`(376)、`VipDaemonApi`(34) → 合计 7004 行
- **controller**：`TenxController`(51)、`TenxApiV2Controller`(52)、`TenxSpiV2Controller`(3)、`InstancesController`(7)、`DashboardsController`(8)、`K8sController`(1)、`K8sProxyController`(5)、`ModeController`(3)、`LokiLogsController`(6)、`PublicComputerController`(10)、`PubVipController`(6)
- **职责一句话**：对 k8s / 时速云 Tenx / Prometheus / Loki 的编排代理：节点、Pod、资源用量、负载均衡、VIP、部署模式。
- **决定性数字**：**105 个端点里只有 2 个触达任何数据库表（2%）**；全候选 7004 行 service 代码里只有 `g_mode` 一张表。**这个候选的主体不是业务，是外部系统适配器（防腐层）。**

### K11 · 白名单（Gateway Whitelist）

- **表**：`g_white_list`；**service**：`WhiteListService`(109 行)；**controller**：`WhiteListController`(3 端点)
- **职责一句话**：网关放行路径白名单，变更后 `GET /refresh` 通知网关生效。

### K12 · 客户端分发（Client Distribution）

- **表**：`g_client`；**service**：`ClientService`(188 行)；**controller**：`ClientController`(6 端点)
- **职责一句话**：客户端安装包上传、列表、批量删除与下载打包。

### K13 · 未归属（本工单刻意不归类，作为切分决策的输入）

| 对象 | 事实 | 为什么不定 |
|---|---|---|
| `g_user_audit` | 被 `RoleService`/`UserService`/`UserAuditService` 三个 service 持有（§1.2 第 35 行）；写入点还包括 AOP：〔源：$BE/aspect/AuditAnnotationAspect.java:70〕 | 它同时是「用户域的从属流水」和「独立审计域」，取决于切分者把审计当从属还是当独立关注点 |
| `g_service` | `ServiceManagementMapper`，**无 service 层**，仅被 `ServiceController`/`PublicController` 直写 | 名字叫"服务管理"，字段却是 CPU/内存上下限（配额），且**配额无人校验**（§4.7） |
| `AppController`(2 端点)、`WebhookController`(1 端点) | 未被归入任何候选 | 体量不足以成域，但属于哪一域需决策 |

---

## 4. 是否含真正的不变量（逐候选，决定值不值得做充血聚合）

### 4.0 先给一把尺子：数据库级硬约束只有 3 条

全 87 个迁移里 `UNIQUE` 只出现 3 次：

| 约束 | 表 | 证据（厂商原文） |
|---|---|---|
| `uni_lang_version_module_key(lang, lang_version, module, text_key)` | `g_language` | `〔源：$MIG/V0.7.4__alter-language-and-init.sql:9〕`「同一语言版本的模块下key唯一」 |
| `uni_lan_version(lang, lang_version)` | `g_language_version` | `〔源：$MIG/V0.7.4__alter-language-and-init.sql:17〕`「语言和版本唯一」 |
| `UNIQUE KEY key(key)` | `g_sub_role` | `〔源：$MIG/V0.7.7__add_sub_role_tables.sql:9〕` |

（`grep -rn 'UNIQUE' $MIG/*.sql` 共 **4** 行：上表 3 条约束，加 〔源：$MIG/V0.8.6__alter-language-unique.sql:3〕 是对第一条的**重复追加修正**，不是第 4 条约束。）

其余 36 张表**没有任何唯一约束**：`g_software.key`、`g_role.key`、`g_user.username`、`g_service_port`（同服务下端口唯一）全部只靠应用层 `select` 后判断。这意味着：**绝大多数"唯一性不变量"在并发下是不成立的**，切分时必须把它们建模成显式的领域规则，否则搬过去也仍然不成立。

另外：**全仓 `synchronized` 只有 4 个位置，且都不在业务路径上**——〔源：$BE/config/websocket/WebSocket.java:97,101,105〕（在线计数，3 处）、〔源：$BE/service/RedisService.java:141〕（连接初始化，1 处）。**没有任何域级互斥锁。**

### 4.1 K1 身份与战位 —— **有真正的不变量（全域最强之一）**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 登录失败次数上限（带 TTL） | 额度 + 状态 | 〔源：$BE/service/UserService.java:276〕：`throw new WrongArgumentException("user-login.error.exceeded.attempts", passwordTimes, ttl)`；计数走 Redis `incr`/`ttl`/`expire`（同文件 265-280） |
| 用户启用/禁用状态门禁 | 状态机 | 〔源：$BE/service/UserService.java:341〕：`throw new SystemException("user.disabled")`；常量 `$BE/constant/UserConstant.java` → `Status.UNAVAILABLE=0 / AVAILABLE=1` |
| 内置战位不可删/不可改 | 互斥保护 | 〔源：$BE/service/RoleService.java:718〕：`if ("admin".equals(key) \|\| "dev".equals(key)) throw "role.cannot.delete.storage"`；〔源：$BE/controller/UserController.java:378〕：`if (user.getUsername().equals("admin")) throw "role.can.not.delete.admin.key"`；`:420` 管理员不可改超管；`:427` 普通用户不可改他人；〔源：$BE/service/UserService.java:1374〕：`"admin".equals(user.getUsername()) && "admin".equals(role.getKey())` 不可移除 |
| 用户名唯一 | 唯一性 | 〔源：$BE/controller/UserController.java:301〕：`user.username.already.exists` |
| 战位标识保留字 | 互斥保护 | 〔源：$BE/controller/RoleController.java:219,222,225〕：`role.can.not.edit.admin.key` / `dev.key` / `role.identifier.reserved.word`；常量表 〔源：$BE/constant/RoleConstant.java:7-10〕（`admin`,`dev`,`cicd`,`root`） |
| 用户 uuid 数据额度 | 额度 | 〔源：$BE/service/UserService.java:499〕：`throw new WrongArgumentException("user.data.limit.exceeded")` |
| 头像大小限制 | 额度 | 〔源：$BE/controller/UserController.java:541〕：`global.avatar.size.limit` |
| 删除战位时的 6 表级联删除的原子性 | 事务 | 〔源：$BE/service/RoleService.java:708-725〕，`@Transactional`（`:707`） |
| 导入 CSV 的角色/用户字段校验 | 校验 | 〔源：$BE/service/RoleService.java:867-883〕（`role.key.is.illegal`、`role.name.is.illegal` 等） |
| `g_sub_role.key` 唯一 | **数据库强制** | 〔源：$MIG/V0.7.7__add_sub_role_tables.sql:9〕 |

**判定：值得做充血聚合。** 这里有明确的保护规则、配额、状态门禁与事务边界。而且这些规则目前**散落在 controller 里**（`UserController` 里判 admin，`RoleController` 里判保留字），是贫血模型造成规则外泄的典型样本。

### 4.2 K2 软件目录与发布 —— **有真正的不变量，但大量关键规则其实由 k8s 强制**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 软件 key / name 唯一 | 唯一性（应用层，无 DB 约束） | 〔源：$BE/controller/SoftwareController.java:173〕：`software.key.identifier.reserved.word`；`:176` `software.key.identifier.existed`；`:179` `software.name.existed` |
| 软件 key 格式规则 | 校验 | 〔源：$BE/service/SoftwareService.java:679,683,687,690〕：不得数字开头/横线开头/格式/长度 |
| 同一软件下路径不可重复 | 唯一性 | 〔源：$BE/service/SoftwareService.java:2311〕：`software.path.exist.in.service` |
| 服务名在同软件下唯一 | 唯一性 | 〔源：$BE/controller/pub/PublicController.java:788〕：`service.duplicate.service.name` |
| 软件版本 = 上传记录 id | 引用完整性 | 〔源：$BE/service/CsSoftwareUploadService.java:238-250〕（`relateCsProgram`，方法起于 `:238`）：写 `software.setVersion(uploadStoreRecordId)`，先校验上传记录存在（缺失抛 `client.missing.cs.upload.file`） |
| 软件发布/启动的状态推导 | 状态（**推导自 k8s，不落库**） | `$BE/constant/RunningStatus.java` 定义 `STARING/RUNNING/STOP/PART_RUN/ROLLING_UPDATE`，但 **该常量在全仓被引用 0 次**；实际值由 〔源：$BE/service/ComputerService.java:1633-1645〕 从 pod `status` 现场计算 |
| 端口占用互斥 | **由 k8s 强制** | 〔源：$BE/controller/ServiceController.java:672〕：捕获 k8s `ApiException`，且 `e.getCode()==422 && responseBody.contains("provided port is already allocated")` 才抛 `service.port.occupied`；`getNodePortUsed()`（同文件 680+）是**查 k8s** 而不是查 `g_service_port` |

**关键判定**：**「同一软件下端口唯一」这条最重要的业务规则，不在 MSOP 的数据库里，在 k8s 里。** `g_service_port` 表存的是映射意图，权威在集群。这意味着 K2 的领域模型**必须承认它是一个外部不变量**，否则任何充血聚合都会写出本地判重然后被集群拒绝的假规则。

**其余判定：值得做充血聚合（唯一性、格式、版本引用），但必须把「端口/发布态」显式建模为「外部系统持有」。**

### 4.3 K3 软件授权与激活 —— **有真正的不变量（全域最强，密码学的）**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 授权码签名校验 | 密码学 | 〔源：$BE/service/LicenceService.java:617〕：`catch (SignatureVerificationException) → licence.invalid.signature`（`checkSoftwareActiveCode` 起于 `:581`） |
| 授权码过期 | 时效 | 〔源：$BE/service/LicenceService.java:621〕：`catch (TokenExpiredException) → licence.activation.code.expired` |
| 机器码绑定（交集判定） | 绑定 | 〔源：$BE/service/LicenceService.java:649〕：`if (!LicenceService.hasIntersectionStream(gaopCodeSet, machineSet)) throw "auth.invalid.auth.machine.id"`；`:558` 是 `hasIntersectionStream` 本身 |
| 授权状态机 0-4 | 状态机 | `$BE/constant/AuthServiceType.java`：`UNAUTHORIZED=0/AUTHORIZED=1/EXPIRED=2/TOBEAUTHORIZED=3/AUTHORIZATION_FAILURE=4`，**全仓被引用 25 次**（如 〔源：$BE/controller/DataBaseController.java:540,551,555〕） |
| 授权方式枚举 | 状态 | `$BE/constant/AuthType.java`：`onceAuth(1)`/`timingAuth(2)`；DB 列注释 〔源：$MIG/V0.2.5__add-service-auth-table.sql:5〕 |
| 授权到期/间隔 | 时效 | 〔源：$MIG/V0.2.5__add-service-auth-table.sql:6〕-7`（`auth_time` 授权到期时间、`auth_interval` 授权间隔） |
| 激活码与机器码混合 | 绑定 | 〔源：$BE/service/LicenceService.java:382〕：`mixActiveCodeMachineCode` |

**未发现**：授权**数量上限**（「最多 N 个节点」）的逻辑。`g_activate` 是公私钥表，无计数字段。**「额度」在本域缺席 —— 未定论，需产品确认是否有意为之。**

**判定：绝对值得做充血聚合，而且是唯一一个「不变量强到值得独立部署」的域。** 它不依赖 k8s、不依赖数据库状态，只依赖密码学与时间。

### 4.4 K4 数据服务 —— **有真正的不变量（状态机 + 引用保护）**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 数据库名唯一 | 唯一性（应用层） | 〔源：$BE/controller/DataBaseController.java:193〕：`database.duplicate.database` |
| 有关联时不可删库 | 引用保护 | 〔源：$BE/service/DatabaseService.java:729〕：`database.not.found.cannot.delete` |
| 数据库用户有关联时不可删/改 | 引用保护 | 〔源：$BE/service/DatabaseUserService.java:192〕：`user.not.found.cannot.delete`；`:214` `user.not.found.modify.failed` |
| 备份任务状态机 0/1/2/3 | 状态机（**裸 int，无常量绑定**） | 写入点：〔源：$BE/service/DatabaseService.java:969〕（`setStatus(1)` = INBACKUP）、`:1091`（`setStatus(2)` = BACKUPCOMPLETE）、`:926,:958,:995,:1004`（`setStatus(3)` = BACKFAILURE）、`:926,:958`（`setStatus(0)` = INIT） |
| SQL 导入的进展/中断 | 状态 | 〔源：$BE/service/DatabaseService.java:1722〕：`global.execution.interruption.exception`；`:2091` `database.no.db.snapshot.backup` |
| 导入文件解析校验 | 校验 | 〔源：$BE/service/DatabaseService.java:1333,1513,1576,1663〕 |

⚠️ **重要反证**：`$BE/constant/BackupType.java` 定义了 `INIT=0/INBACKUP=1/BACKUPCOMPLETE=2/BACKFAILURE=3`，与上面的裸 int 语义完全对应，但**该常量在全仓被引用 0 次**（`grep -rn "BackupType\." --include=*.java` 无输出）。同理 `BackupSettingType`、`SyncMethod`、`RunningStatus` 引用数均为 0。

**判定：有真实不变量（状态机 + 引用保护），值得充血聚合；但状态机目前是「裸数字」，聚合时必须先把 `BackupType` 这类死常量激活成真正的值对象。**

### 4.5 K5 备份恢复 —— **有真正的不变量（单例互斥 + 任务状态）**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 同一时刻只允许一个备份 Job | 互斥（Quartz 单例） | 〔源：$BE/service/BackService.java:386-389〕：`if (scheduler.checkExists(new JobKey("backupJob","group1"))) { log.warn("Backup job already exists."); throw new WrongArgumentException("Backup job already exists.") }` |
| 备份标识有效性 | 校验 | 〔源：$BE/service/BackService.java:1063〕：`backup.invalid.backup.identifier` |
| 备份包内容项必须齐全 | 完整性 | 〔源：$BE/service/BackService.java:1238-1330〕：`backup.server.{user,storage,software,lb,config.group,business.db,image}.not.found`（7 项逐项校验） |
| 恢复的进度状态 | 状态 | 〔源：$BE/service/BackupLogService.java:28-29〕：`public static List<BackupRecoveryLog> backLogs / recoveryLogs` —— **状态存在静态 List 里**（进程内） |

⚠️ 反证：`backupLogs`/`recoveryLogs` 是 `public static List`，多实例部署时进度不可见；备份状态**没有任何数据库字段承载**。

**判定：有真实不变量，但载体是 Quartz + 静态内存，不是数据库。** 值得聚合，但聚合后要解决多实例问题——这本身是切分时要看到的证据。

### 4.6 K6 镜像与制品 —— **有真正的不变量（唯一性 + 状态机）**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 同用户下镜像名唯一 | 唯一性（应用层） | 〔源：$BE/service/CicdService.java:283〕：`image-auto-build.illegal.image.name`（正则 `^[a-zA-Z0-9-]+$`）；`:287-290`：select 后 `if (select.size() > 0) throw "image-auto-build.duplicate.image.name"` |
| 镜像仓库分组名唯一 | 唯一性 | 〔源：$BE/service/impl/K3sImageServiceImpl.java:178〕：`image.duplicate.repository.group` |
| 镜像上传日志状态 0/1/2 | 状态机 | 表注释 `$MIG/V0.4.1__add-image-log-table.sql`：「状态。0：失败，1：成功，2：处理中」；写：〔源：$BE/service/DockerService.java:108-118〕（失败/成功）、`:158-167`（insert）；读：〔源：$BE/service/impl/K3sImageServiceImpl.java:315〕（`status isEqualTo 1` 过滤成功镜像） |
| Dockerfile 内容必填 | 校验 | 〔源：$BE/service/CicdService.java:317〕：`image-auto-build.empty.dockerfile.data` |
| 构建任务存在性 | 引用 | 〔源：$BE/service/CicdService.java:312〕：`image-auto-build.build.task.not.found` |

**判定：有真实不变量（唯一性 + 三态日志），值得聚合；但唯一性是 `select-then-insert`，无 DB 约束，**并发下可破**。

### 4.7 K7 文件与仓库 / K11 白名单 / K12 客户端 —— **薄，但各有 1 条真规则**

**K7**
| 不变量 | 证据 |
|---|---|
| FastDFS 上传的去重/追加/CRC 校验 | 〔源：$BE/service/FileService.java:493,506,547〕：`fdfs.append.file.size.failed` / `fdfs.upload.file.crc.failed` |
| 上传与 DB 记录的一致性（先上传后 insert，失败要回滚） | 〔源：$BE/service/FileService.java:430-443〕：`fdfs.upload.file.failed` → `database.insert.failed`；`:489` `fdfs.upload.file.stopped` |
| 断点续传 | 〔源：$BE/service/FileService.java:936,1249〕：`fdfs.stop.upload.failed.id.not.exist` / `fdfs.no.restart.ip.specified` |
| 文件名唯一性（DB 索引非唯一） | 〔源：$MIG/V0.6.1__add-file-index.sql:1〕（`index projectId on g_file(file_project_id, name)`）—— **是普通索引，不是唯一索引** |

**判定：K7 的规则是「外部存储一致性」，不是领域规则。** `File` 的充血价值低，但**跨系统一致性（FastDFS + DB）是真实难点**，值得单独建模为「文件暂存/提交」两阶段，而不是散在 `FileService` 的 72 个方法里。

**K11**：〔源：$BE/service/WhiteListService.java:46-52〕 的 `addPath`：先 select 判重（`software.path.already.exists`），再 insert，再 `notificationGateway()`。**唯一一条真规则 + 一个外部副作用（通知网关）**。〔源：$BE/service/WhiteListService.java:75〕：`white.list.delete.path.not.exist`。109 行、3 端点 —— **纯规模型域，充血收益极小**。

**K12**：`$BE/service/ClientService.java` 12 个方法、10 个 throw，但全部是「文件不存在/类型非法」（`client.client.not.exist:110`、`file.not.found:119`、`client.delete.file.not.exist:163`）。**判定：基本是文件 CRUD 转发，无领域不变量。**

### 4.8 K8 平台设置与国际化 —— **有真正的不变量（且是全域唯一有 DB 强制的）**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 语言版本「全局只有一个 used=1」 | **互斥（单活跃）** | 〔源：$BE/service/impl/LanguageServiceImpl.java:462-465〕：先 `set(used).equalTo(0).where(used = 1)` 再 `set(used).equalTo(1).where(lang, version)` —— 与 K10 的 `ModeServiceImpl` 是**同一个模式**（§4.9） |
| 词条三元组唯一 | **数据库强制** | 〔源：$MIG/V0.7.4__alter-language-and-init.sql:9〕 + 〔源：$MIG/V0.8.6__alter-language-unique.sql:3〕（厂商两次修这条约束） |
| 语言+版本唯一 | **数据库强制** | 〔源：$MIG/V0.7.4__alter-language-and-init.sql:17〕，并带 `idx_used` 支撑单活跃查询（`:18`） |
| 导入 key 去重 | 唯一性 | 〔源：$BE/service/impl/LanguageServiceImpl.java:460〕：`language.import.duplicate.key`（把重复 key 拼成字符串一起返回） |
| 导入字段非空 | 校验 | 〔源：$BE/service/impl/LanguageServiceImpl.java:174,192,225〕（module/key/content must not null） |
| 默认语言必须是基础语言 | 校验 | 〔源：$BE/service/impl/LanguageServiceImpl.java:469〕：`BaseLanguageEnum.isNotBaseLanguage(...)` |

**判定：值得聚合，而且这是「聚合根 = 语言版本」的教科书场景**：单活跃互斥 + 复合唯一键 + 导入去重，全在一个聚合里。**但 `g_setting` 与 `g_dictionary` 是被硬塞进来的**——`g_setting` 被 6 个 service 跨域读写（§1.2 第 28 行），`g_dictionary` 只服务审计。这是**一个候选里混了两种东西**的信号。

### 4.9 K9 告警 —— **有真正的不变量（去重 + 引用完整性）**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 按 fingerprint 去重（重复告警更新而非新增） | 幂等/去重 | 〔源：$BE/service/AlertService.java:93〕：`receiveResolve` 用 `fingerprint isIn` 捞；`:135-140`：`repeatRecords` 命中则 `updateByPrimaryKey`；`:142-143`：过滤掉 `repeatFingerprints` 与不在 `containedRuleIds` 里的记录 |
| 告警必须归属到已存在规则 | 引用完整性 | 〔源：$BE/service/AlertService.java:142〕：`ruleIds isIn` → `containedRuleIds`，随后 `:145` 取 `alertRule` |
| 策略启停要同步到 Loki | 外部副作用 + 事务性 | 〔源：$BE/service/AlertService.java:159-164〕：`enableAlert` 先查策略（`:160` `alert.id.not.exist`）、再查该策略下全部规则（`:163`）、再调 Loki API |
| 策略/规则的新增/更新/删除成套 | 聚合一致性 | 〔源：$BE/service/AlertService.java:180-184〕：`updateAlertStrategy` 里分离"带 id 的更新"与"无 id 的新增" |

**判定：值得聚合。** 26 个方法、580 行，规则密度高于 K2/K7。缺点是**入口不在自己的 controller 里**。

### 4.10 K10 集群运维 —— **几乎没有业务不变量，只有 1 个互斥**

| 不变量 | 类型 | 证据 |
|---|---|---|
| 部署模式「全局只有一个 used=1」 | **互斥（单活跃）** | 〔源：$BE/service/impl/ModeServiceImpl.java:147-148〕：`changeModeTypeDB(id)` 先清零再置一；切换后 `:142` 发 Kafka `sendModeChanged` |
| 入网/ingress 路径重复 | 校验（外部） | 〔源：$BE/service/TenxService.java:2693〕：`k8s.ingress.path.repeat` |
| 端口范围/类型 | 校验（外部） | 〔源：$BE/controller/ServiceController.java:2919〕：`service.exposed.port.range.invalid` |
| 节点重启记录去重 | 幂等 | 〔源：$BE/ApplicationStarted.java:353-363〕：先 select `podEvent(serviceName,podName,restartNum)` 再 insert |
| VIP 保护 IP 不可删 | 互斥保护 | 〔源：$BE/controller/pub/PubVipController.java:123〕：`vip.delete.protected.ip` |

**其余 90+ 个 throw 全是外部系统失败转译**：`k8s.get.pod.ip.failed`、`k8s.call.api.failed`、`ssh.connect.node.ip.failed`、`service.yaml.to.v1-deployment.error`、`query.time.range.must.between.720`（〔源：$BE/controller/cloud/TenxCloudImpl.java:174〕）等。

**判定：不值得做充血聚合。** 105 端点 / 6628 行 service，但**1.9% 触库、0 个领域状态机（`g_mode.used` 除外）**。它的本质是**防腐层（ACL）+ 编排器**：输入是 k8s/时速云的响应，输出是 DTO。把它做成"聚合"会造出一堆只有 getter/setter 的假实体。

> ⚠️ 但 `ModeServiceImpl` 的「单活跃」是**真不变量**，且它与 §4.8 的 `LanguageServiceImpl` 用的是**完全相同的模式**（先 `used=0 where used=1`，再 `used=1 where id`）。这是两个候选之间**同一规格的重复实现**——切分时应抽成一个共享的「单活跃集合」抽象，而不是各写一遍。

### 4.11 K11 / K12 —— 见 §4.7。**结论：不值得充血。**

### 4.12 不变量总览（一屏决策表）

| 候选 | 状态机 | 额度/配额 | 互斥/唯一 | 审批/多步 | DB 强制 | 判定 |
|---|---|---|---|---|---|---|
| K1 身份与战位 | ✅ 用户启停 | ✅ 登录次数/uuid 额度/头像大小 | ✅ 内置战位保护、用户名唯一、子角色 key | ❌ | ✅ `g_sub_role.key` | **值得充血** |
| K2 软件目录与发布 | ⚠️ 推导自 k8s | ❌ | ✅ key/name/路径/服务名唯一（均应用层） | ⚠️ 导入导出有多步 | ❌ | **值得，但端口规则属 k8s** |
| K3 软件授权与激活 | ✅ 5 态 | ❌ 未发现数量额度 | ✅ 机器码绑定、签名 | ❌ | ❌ | **最值得（密码学）** |
| K4 数据服务 | ✅ 备份 4 态（裸 int） | ❌ | ✅ 库名唯一、关联禁删 | ⚠️ SQL 导入进度 | ❌ | **值得** |
| K5 备份恢复 | ✅ 任务进度 | ❌ | ✅ Quartz 单例 | ✅ 多步（7 项校验） | ❌ | **值得**（载体是内存） |
| K6 镜像与制品 | ✅ 日志 3 态 | ❌ | ✅ 镜像名/分组唯一 | ⚠️ 构建任务 | ❌ | **值得**（唯一性弱） |
| K7 文件与仓库 | ❌ | ❌ | ⚠️ 文件名（非唯一索引） | ✅ 断点续传 | ❌ | **边际**；真难点是外部一致性 |
| K8 平台设置与国际化 | ✅ 版本单活跃 | ❌ | ✅✅ 复合唯一（**DB 强制**） | ✅ 导入/导出 | ✅ ×2 | **值得**（语言部分） |
| K9 告警 | ✅ 记录幂等 | ⚠️ 阈值在 Loki 侧 | ✅ fingerprint 去重 | ❌ | ❌ | **值得** |
| K10 集群运维 | ✅ 模式单活跃（仅 1） | ❌ | ❌ | ❌ | ❌ | **不值得充血，是 ACL** |
| K11 白名单 | ❌ | ❌ | ✅ 路径唯一 | ❌ | ❌ | **不值得** |
| K12 客户端分发 | ❌ | ❌ | ❌ | ❌ | ❌ | **不值得** |

**「审批/多人流程」在全仓未发现**：`grep -rni 'approve|approval|workflow|multistep' --include=*.java` 的命中全部落在 vendored 的 k8s 客户端模型注释里（`$BE/third/k8s/*.java`），无一条是本项目代码。**结论：MSOP 没有任何审批流。** 这一条可以排除"审批"作为切分维度的可能性。

---

## 5. 纯 CRUD 转发清单

### 5.1 度量口径

「纯转发方法」= 方法体只有 1 条语句且以 `return` 开头。生产脚本见附录 A 的 `deleg()`。

### 5.2 controller —— 纯转发占比 ≥50% 的类（靶子）

| controller | 端点方法 | 纯转发 | 占比 | 代码行 |
|---|---|---|---|---|
| `ExSettingController` | 4 | 4 | **100%** | 39 |
| `ModeController` | 3 | 3 | **100%** | 48 |
| `RegisterController` | 1 | 1 | **100%** | 28 |
| `LokiLogsController` | 6 | 5 | 83% | 67 |
| `K8sProxyController` | 5 | 4 | 80% | 223 |
| `InstancesController` | 7 | 4 | 57% | 81 |
| `AppController` | 2 | 1 | 50% | 50 |
| `GlobalSettingController` | 6 | 3 | 50% | 78 |

> 这 8 个类的**全部业务逻辑都在 service 里**，controller 只是一个 HTTP 壳。另外 `DatabaseRecordController`(1 端点)、`ComponentController`(1)、`CreateK8sServicesController`(1)、`K8sController`(1)、`WebhookController`(1)、`ImageUploadWebhookController`(1)、`WebsocketController`(0) 因为只有 1 个端点 / 0 个端点，未参与比率计算，但**同样没有任何业务判断**。

### 5.3 service —— 无业务规则的类（纯 CRUD / 纯转发 / 纯基础设施）

**A. 纯 CRUD 转发（无规则、无分支）**

| service | 方法 | `if` 数 | `throw` 数 | 代码行 | 说明 |
|---|---|---|---|---|---|
| `DatabaseRecordService` | 5 | 1 | 1 | 55 | `getAllDatabaseRecord` 分页 + `addDatabaseRecord` 拼 UUID 插入；状态由调用方传入 |
| `FileProjectService` | 7 | 3 | 3 | 77 | 只有 `software.name.existed` / `software.delete.failed.not.exist` 两条存在性检查 |
| `KafkaProducerService` | 7 | **0** | **0** | 43 | 纯发送 |
| `KafkaService` | 3 | **0** | **0** | 37 | 纯发送 |
| `VipDaemonApi` | 7 | **0** | **0** | 34 | 纯 HTTP 客户端 |
| `SqlImportProgressListener` | 1 | 0 | 0 | 10 | 纯回调 |
| `ComponentService` | 2 | 1 | 0 | 35 | 纯 k8s 读取 |
| `LanguageService` | 8 | 0 | 0 | 28 | 接口（实现 `LanguageServiceImpl` 有规则） |
| `ModeService` | 5 | 0 | 0 | 20 | 接口（实现 `ModeServiceImpl` 有单活跃） |
| `SubRoleService` | 24 | 0 | 0 | 70 | 接口（实现 `SubRoleServiceImpl` 有规则） |
| `NodeVersionService` | 3 | 0 | 0 | 15 | 接口 |

**B. 纯转发但非纯 CRUD（有 1 条规则，转发比高）**

| service | 纯转发/方法 | 占比 | 说明 |
|---|---|---|---|
| `K8sService` | 5/9 | 56% | 纯 k8s 门面 |
| `K8sImageServiceImpl` | 11/23 | 48% | 镜像仓库门面，规则少 |
| `ExSettingService` | 3/6 | 50% | 全部委托给 `DynamicLoading`（〔源：$BE/service/ExSettingService.java:15-18〕） |
| `NodeVersionServiceImpl` | 3/10 | 30% | 门面 |

**C. 无表、无领域规则的基础设施（不应进入任何上下文）**

`RedisService`(151 行，token/登录列表/计数)、`KafkaProducerService`、`KafkaService`、`BackupLogService`(WebSocket 推送，〔源：$BE/service/BackupLogService.java:28-29〕 静态 List)、`DockerRegistryApiService`、`LokiApiService`、`AbstractImageService`(抽象基类)。

### 5.4 DAO 层：21 个是零逻辑的生成物

40 个**拥有表的**逻辑 DAO 中：

- **21 个**是 MyBatis 生成的纯 CRUD（含 `MyBatis3Utils`，无任何手写 SQL）：
  `ActivateMapper`、`AlertRecordMapper`、`AlertRuleMapper`、`AlertStrategyMapper`、`BackupMapper`、`BackupSettingMapper`、`CicdMapper`、`DatabaseExecLogMapper`、`DatabaseRecordMapper`、`DictionaryMapper`、`FileProjectMapper`、`ImageGroupMapper`、`LanguageVersionMapper`、`ModeMapper`、`PodEventMapper`、`ServiceAuthMapper`、`ServiceManagementMapper`、`SoftwareAppMapper`、`SubRoleMapper`、`SubRoleRoleMapper`、`WhiteListMapper`。
- **18 个**是「生成 CRUD + 手写 SQL」混合。
- **1 个**是纯手写：`DataBaseManagerMapper`（**6 条** SQL 注解，全部只打 `g_database`）。

另有一个**不拥有任何 `g_*` 表**的手写 Mapper：`$BE/dao/mysql/MysqlMapper.java` —— 它操作的是 MySQL 系统库（`show databases`、`information_schema.SCHEMATA`、`mysql.user`、`create database`、`GRANT ALL ON ...`），并用 `@DataSource(DbType.MYSQL)` 切到独立数据源。**它是 K4 的"真实 DDL 执行器"，与领域表完全解耦——这是 K4 内部一个天然的、可独立的技术边界。**

**这 21 个零逻辑 DAO 的存在，是"贫血"的直接量化证据**：任何"充血聚合"改造都不会碰到它们，它们只是持久化细节。

---

## 6. 反例讨论（每个候选至少一条「看起来像 A，其实属于 B」）

### K1 身份与战位

**反例 1：`g_role_software` 看着像战位表（K1），其实属于 K2。**
名字前缀是 `g_role`，且 `RoleSoftwareMapper` 被 `RoleService` 直接持有（`$BE/service/RoleService.java` 的 DAO 字段区）。但它表达的是**「某战位可以使用哪些软件」**——即软件目录的授权清单。证据：〔源：$BE/dao/SoftwareMapper.java:132〕 的裸 SQL 把它和 `g_role` `INNER JOIN` 起来只为查 `app_id`（软件 id），而 `:141` 的 `update` 是 `delete from g_role_software where app_id=#{id}`——**按软件 id 删关系**。读写都以软件为主键，说明它的聚合根在 K2 侧。
**切分提示**：如果按表名归类，会把 2 张 DAO（`RoleSoftwareMapper`+`SoftwareMapper`）和 5 个 service 的边全部漏掉。

**反例 2：`g_user_login` 看着像认证会话（K1 的登录子域），其实是「战位登录态」的清理附带物。**
`§1.2` 第 36 行显示它**只被 `RoleService` 一个 service 承载**（精确 2 个端点）。登录路径实际把 token 写 Redis（`docs/findings/02` §6.1 已证 `g_user_login` 在登录路径未被写入）。【推断】它更像是删除战位时的级联清理目标（〔源：$BE/service/RoleService.java:722〕 在删战位时 `userLoginMapper.delete`）。
**切分提示**：把它当"会话域"独立会得到一个空域。

### K2 软件目录与发布

**反例 1：`SoftwareBackupV3Controller` / `SoftwareImportV3Controller` 看着像备份/导入（K5/K7），其实属于 K2。**
它们的"备份"用的是 `Caffeine` 进程内缓存而不是 `g_backup`：`$BE/service/software/backup/SoftwareBackupTaskManager.java` 用 `Cache<String, SoftwareBackupTaskStatus>`（`expireAfterWrite(2, HOURS)`、`maximumSize(1000)`），完全不碰 `g_backup`/`g_backup_setting`。真正的 `g_backup` 由 `BackService`（K5）使用。**两套"备份"同名不同域。**

**反例 2：`g_pod_event` 看着像事件/告警域（K9），其实属于 K2 的服务运行态。**
`g_pod_event` 记录的是**容器重启次数**（〔源：$BE/ApplicationStarted.java:353-363〕：select by `serviceName+podName+restartNum` 再 insert）。写入者是启动组件与 `ServiceController`（〔源：$BE/controller/ServiceController.java:274〕 直连 `PodEventMapper`），读取者也是 `ServiceController:3954`。与 `g_alert_*`（Loki 告警）无任何代码关联。

**反例 3：`ServiceController` 看着是"服务发布域"（K2），其实 4108 行里主体是 k8s 编排（K10）。**
它的 51 个端点前 10 个里就有 `POST /v1/service/{serviceName}/nodePort`、`PUT /v1/service/patchCommand/{serviceName}`、`GET /v1/service/native`、`GET /v1/service/labels`——这些直接对 k8s API 操作。§2.6 显示它同时直连 6 个 Mapper。**它同时是 K2 与 K10 的实现类**，不能整体归任何一边。

### K3 软件授权与激活

**反例：`AuthController` 看着是"认证"（AuthN，属 K1），其实是"授权"（AuthZ，属 K3）。**
`@RequestMapping("/auth")`（〔源：$BE/controller/AuthController.java:83〕），端点 `gene_identifier`、`admin/auth`、`authing`、`cancelAuth`、`updateAuth`、`updateAuthInfo` 全部是**软件授权码流程**，抛的错是 `auth.parse.authorization.code.error:181`、`service.auth.code.expired:170`、`auth.invalid.signature:174`。它与 `g_user`/`g_role` 的唯一交集是它注入了 `UserService`（做操作人身份）——但那是在**用**K1，不是在定义 K1。
**切分提示**：按 URL 前缀 `/auth` 归类会把它错误并入身份域；实际它与 `LicenceController`（`/v1/licence`）是同一域。**证据：两者共用 `g_auth`/`g_activate`/`g_service_auth` 三张表。**

**反例 2：`g_service_auth` 看着是"服务（K2）的鉴权"，其实是软件授权状态。**
列注释 〔源：$MIG/V0.2.5__add-service-auth-table.sql:3〕-7`：`service_id`（服务id）、`auth`（是否授权）、`auth_type`（一次性/定时认证）、`auth_time`（授权到期时间）、`auth_interval`（授权间隔）。这里的 "service" 指的是**被授权的软件产品**，不是 k8s service。

### K4 数据服务

**反例：`DataBaseController` 看着是数据库域（K4），但它直连 `SoftwareMapper` 与 `ServiceAuthMapper`（K2/K3）。**
证据：〔源：$BE/controller/DataBaseController.java:106,108〕。它的 23 个端点有 4 个是纯转发，剩下的是数据库操作 + 软件授权检查（`:540` `st2.setAuth(AuthServiceType.AUTHORIZED)`）。**它是 K4 的端点宿主，但它的鉴权逻辑读 K3 的表。**

**反例 2：`BackService`（K5）也在写 `g_backup`——但 `DatabaseService`（K4）同样注入 `BackupMapper`+`BackupSettingMapper`。**
§1.2 第 6、7 行显示 `g_backup`/`g_backup_setting` 有 **2 个承载 service**：`BackService` 与 `DatabaseService`。数据库的备份逻辑（〔源：$BE/service/DatabaseService.java:926-1178〕 的状态写入）就在 K4 的 service 里。**"备份"这个能力被 K4 与 K5 各实现了一半。**

### K5 备份恢复

**反例 1：`TestController` 被归入 K5（它注入 `BackService`），但它 4 个端点的语义是"测试"。**
证据：〔源：$BE/controller/TestController.java:29,31,33〕 直连 `ModeMapper`/`SubRoleMapper`/`RoleMapper` —— **一个测试控制器持有 3 张核心表的 Mapper**，横跨 K1/K2/K10。它不属于任何域，应作为**技术债单独处理**（`grep -n '@GetMapping' controller/TestController.java` 为 `/v1/test/*`）。

**反例 2：`g_backup_setting` 看着像平台设置（K8 的 `g_setting`），其实是备份参数。**
两个表名相近、领域完全不同；`BackupSettingType`（`DATA_ONLY_STRUCTURE=0` / `STRUCTURE_AND_DATA=1`，`$BE/constant/BackupSettingType.java`）是备份专属枚举，与 `SettingKey` 无交集。

### K6 镜像与制品

**反例 1：`g_group` 看着是通用"分组/组织"（可能被读成用户组或权限组），其实是**镜像仓库分组**（`ImageGroup`）。**
证据：support 类名是 `ImageGroupDynamicSqlSupport`（〔源：$BE/dao/ImageGroupDynamicSqlSupport.java:23〕→ `super("g_group")`），且它的读写点全在 `K3sImageServiceImpl` 的 `imageGroupMapper` 上（〔源：$BE/service/impl/K3sImageServiceImpl.java:100〕声明字段；:176,:183,:188,:195,:196,:215,:491,:508 读、:717,:731 删），报错键是 `image.duplicate.repository.group`（〔源：$BE/service/impl/K3sImageServiceImpl.java:178〕）。**表名 `g_group` 具有误导性，是切分时的命名陷阱。**
> 这张表还提供了一个方法论教训：它在本工单的第一版模型里被记成「0 端点」，因为唯一的承载类 `K3sImageServiceImpl` 被错误地并进了接口 `AbstractImageService`。**承载关系的分母是具体类，不是接口**（§0.2 口径警告 2、§1.3）。

**反例 2：`K3sImageServiceImpl` / `K8sImageServiceImpl` 看着是 k8s 适配器（K10），其实实现了 K6 的镜像仓库抽象。**
事实：`AbstractImageService` 是**接口**（〔源：$BE/service/AbstractImageService.java:18〕：`public interface AbstractImageService`），有**两个**实现：`K3sImageServiceImpl`（〔源：$BE/service/impl/K3sImageServiceImpl.java:90〕）与 `K8sImageServiceImpl`（〔源：$BE/service/impl/K8sImageServiceImpl.java:57〕）。两个实现都持有 `ImageLogMapper`（〔源：$BE/service/impl/K3sImageServiceImpl.java:102〕、〔源：$BE/service/impl/K8sImageServiceImpl.java:65〕），**但只有 K3s 那个持有 `ImageGroupMapper`**（〔源：$BE/service/impl/K3sImageServiceImpl.java:100〕 对 `K8sImageServiceImpl.java` 全文件无 `ImageGroupMapper` 的 grep 反证）。
控制器以**接口类型**注入：〔源：$BE/controller/ImageController.java:83〕与〔源：$BE/controller/ServiceController.java:282〕都是 `private AbstractImageService imageServiceInstance;`。
**含义**：`K3s`/`K8s` 是**同一抽象按集群类型的策略实现**，不是域边界；但**「哪张表会被写」取决于运行期选中的实现** —— 因此对 `g_group`，「端点」这个概念在这个 service 上是**运行期二义的**。切分时若把镜像域独立，必须显式声明 `g_group` 只有 K3s 实现会碰。

### K7 文件与仓库

**反例：`g_file` 看着是通用文件域，其实被 K4 与 K6 当自己的子表用。**
§1.2 第 15 行：`g_file` 的承载 service 是 `CicdService`(K6)、`DatabaseService`(K4)、`FileService`(K7) —— **3 个候选共写一张表**。`DatabaseService` 用它存 SQL 文件（〔源：$BE/service/DatabaseService.java:929〕 `global.save.file.failed`），`CicdService` 用它存 Dockerfile（〔源：$BE/service/CicdService.java:371〕 `fdfs.upload.file.failed`）。
**切分提示**：若把 `g_file` 划给 K7，K4/K6 就必须通过 K7 的服务访问文件——这是切分后**必须新增的接口**，不是可以忽略的。

**反例 2：`CsSoftwareUploadService` 看着是"客户端软件上传"（K12 客户端分发），其实是 K2 的软件版本发布。**
它的核心动作是 `relateCsProgram` / `rollBackVersion`（〔源：$BE/service/CsSoftwareUploadService.java:238-275〕）：把 `software.version` 指向某条上传记录 id。它注入的是 `SoftwareService`+`StoreMangeService`（K2），不是 `ClientService`（K12）——**两条边都没有**。

### K8 平台设置与国际化

**反例 1：`g_user_audit` 看着属于 K1（用户），其实与 K8 的审计字典同域。**
承载 service 是 `RoleService`/`UserAuditService`/`UserService`（§1.2 第 35 行），其中只有 `UserAuditService` 同时持有 `DictionaryMapper`（`g_dictionary`）与 `UserAuditMapper`。而且写入的**主路径是 AOP**：〔源：$BE/aspect/AuditAnnotationAspect.java:70〕 持有 `UserAuditMapper`，19 个 controller 上有 `@AuditLog`。
**切分提示**：审计是**横切关注点**，把它并入 K1 会让 K1 被所有域的写操作依赖。

**反例 2：`g_setting` 看着是 K8 的核心表，其实是被 6 个候选当全局变量池使用。**
§1.2 第 28 行：`AbstractImageService`(K6)、`FileService`(K7)、`LanguageService`(K8)、`LicenceService`(K3)、`SettingService`(K2)、`UserService`(K1) 六个 service 直接持有 `SettingMapper`，精确端点数 19、上界 115。`$BE/constant/SettingKey.java` 是一张横跨所有域的 key 清单。
**切分提示**：`g_setting` 是**典型的共享内核（Shared Kernel）候选**，不是某个域的实体。

### K9 告警

**反例：`AlertService` 的入口全在 `TenxApiV2Controller`（`/cloud/alert*`），看着属于 K10 集群运维，其实是一个独立域。**
证据：〔源：$BE/controller/cloud/TenxApiV2Controller.java:133〕 的 `receiveAlert` 只是把 Loki webhook 转给 `alertService.receiveFiring`；`:167-235` 的 9 个端点全部一对一转发给 `AlertService`。而 `AlertService` 的 3 张表与 k8s 无任何关联，它的领域规则是 fingerprint 去重。
**切分提示**：K9 的**代码归属正确（独立 service + 独立表），但端点载体错误（寄生在 K10 的 controller 里）**。切分时必须把端点重新挂到 K9 自己的 controller 上，否则 K9 永远依赖 K10 的部署单元。这是「按端点切分」会犯错的地方。

**反例 2：`g_alert_record` 看着像"日志/记录"（可能与 K8 审计合流），其实是有幂等键的业务记录。**
`fingerprint` 是它的业务主键（〔源：$BE/service/AlertService.java:93,135〕），不是日志。审计流水（`g_user_audit`）没有幂等语义。

### K10 集群运维

**反例 1：`ModeService` 有表 `g_mode`，看着像"业务配置域"，其实属于部署编排。**
`ModeServiceImpl` 的全部动作是切 k8s Deployment 的环境变量：〔源：$BE/service/impl/ModeServiceImpl.java:135-140〕（`updateServiceEnv` → `deploymentApi.patchNamespacedDeploymentCall`，`:203`）。`g_mode` 只是记"当前用的是哪个模式"。**它的表是编排状态的存档，不是领域实体。**

**反例 2：`ComputerService`（1853 行 / 57 方法）看着像"业务域最大的一个"，其实 0 张表。**
§3 表显示 `ComputerService` 的 DAO 数是 0，`if` 数 117 但全是 k8s 资源判断（〔源：$BE/service/ComputerService.java:663〕 `ssh.node.not.found.query.pod.info`、`:688` `k8s.get.cluster.resource.info.failed`）。**代码行数完全不能作为领域大小的代理指标** —— 这是本工单对切分最重要的方法论提醒。

**反例 3：`PubVipController`（385 行）看着像"公网 VIP 业务"，其实是 k8s/SSH 网卡操作。**
`$BE/controller/pub/PubVipController.java` 抛的是 `ssh.cannot.get.network.info:256`、`ssh.fetch.net.failed:218`、`k8s.get.node.info.failed:177`。唯一业务规则是 `vip.delete.protected.ip:123`。

### K11 白名单

**反例：`WhiteListService` 看着是独立的"安全/网关"域，其实是 K2 软件发布的一个附属动作。**
证据：`g_white_list` 的承载 service 是 `SoftwareService`(K2) 与 `WhiteListService`（§1.2 第 39 行）—— **K2 的 `SoftwareService` 直接持有 `WhiteListMapper`**。而且 `WhiteListService.addPath` 的最后一步是 `notificationGateway()`（〔源：$BE/service/WhiteListService.java:59〕），与 `SoftwareService.notificationGateway()` 是同一个网关通知机制（`docs/findings/02` §2.1 已证两者都是 `GET /refresh`）。
**切分提示**：109 行 / 3 端点 / 1 表，做成独立上下文是**过度切分**的典型。

### K12 客户端分发

**反例：`ClientService` 看着是"客户端软件包管理"（像 K7 的兄弟），其实是独立于软件目录的另一条分发路径。**
`g_client` 表的语义是「客户端安装包」，与 `g_software`（平台管理的应用）无外键、无代码引用交叉：`ClientService` 只注入 `ClientMapper`（§3 K12，DAO=1），不注入 `SoftwareService`。反过来，`CsSoftwareUploadService`（K2）处理"CS 主程序"时用的是 `g_software`+`g_upload_store_record`，也不碰 `g_client`。
**结论**：二者名字都含"客户端/软件"，但**确实是两条独立链路**——这是本工单里唯一一个「看着像重复、实际确实该分开」的反例。

---

## 7. 未定论 / 需后续工单确认

| # | 未定论项 | 为什么定不了 | 建议由谁决策 |
|---|---|---|---|
| 1 | 端点总数 604 vs `docs/findings/02` 的 605 | 差异 1 个，未定位（可能是注解跨行或 `@RequestMapping` 方法级写法）。**影响力极小，不影响任何结论** | 本工单自认未定论 |
| 2 | 是否真有「授权数量额度」 | `$BE/constant/AuthServiceType.java` 只有状态没有计数；`g_activate` 无计数字段。**代码中未发现**，但产品可能有意不做 | 产品 |
| 3 | `ServiceManagement`（`g_service`）的 CPU/内存上下限是否本应生效 | 只有 〔源：$BE/controller/pub/PublicController.java:941-943〕 写入（`setMinMemory("0")`、`setMaxCpu(...)`），**无任何读取方做校验** | 产品 + 架构 |
| 4 | `g_user_audit` 归属 | 见 §3.13 与 §6 K8 反例 1 | 架构 |
| 5 | `g_setting` 是共享内核还是拆成各域私有配置 | 6 个候选直写（§2.3），`SettingKey` 横跨全域 | 架构 |
| 6 | `BackService`（横切 12 个 service）该不该独立上下文 | 见 §2.4 | 架构 |
| 7 | `g_database_exec_log` / `g_group` 无端点表的归属 | 见 §1.3 | 架构 |
| 8 | 24 个未解析的 controller→service 调用点 | 见 §2.7；需逐点人工确认是否漏表 | 本工单后续可补 |
| 9 | 是否存在 DB 触发器/存储过程承载的不变量 | 迁移文件中未发现 `CREATE TRIGGER`/`PROCEDURE`，但**未逐文件穷举验证** | 后续工单 |
| 10 | 前端 196 条之外是否还有直接调后端的路径 | `$WEB/api-catalogue.json` 是唯一调用面清单，未核对前端 chunk 中是否有手写 `fetch` | 后续工单 |

---

## 附录 A · 可复现脚本

单文件、仅用 Python 3 标准库。保存为 `msop_ctx.py`，在任意位置执行：

```bash
python3 msop_ctx.py /Users/csm/Desktop/Project/msop
```

输出：§0.2 的计数、§1.2 矩阵所需的「表→DAO」「表→service→端点」，以及 §2.6 的「controller 直连 DAO」与「service→service 注入」。全程只读。

```python
#!/usr/bin/env python3
# MSOP 03-context-evidence 复现脚本（只读，仅标准库）
#   python3 msop_ctx.py /Users/csm/Desktop/Project/msop
import os, re, collections, sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BE = os.path.join(ROOT, "recovered/gaop-be/src/main/java/com/glriverside/gaop/be")
read = lambda p: open(p, encoding="utf-8", errors="replace").read()
def walk(d):
    for dp, _, fns in os.walk(d):
        for f in sorted(fns):
            if f.endswith(".java"): yield os.path.join(dp, f)
def methods(txt):
    L = txt.split("\n"); st = [i for i, l in enumerate(L) if re.match(r'^    (public|private|protected)\s', l)]
    return [(st[k]+1, "\n".join(L[st[k]:(st[k+1]-1 if k+1 < len(st) else len(L))])) for k in range(len(st))]
def fields(txt):
    d = collections.defaultdict(list)
    for m in re.finditer(r'\sprivate\s+(?:static\s+|final\s+)*([A-Z][A-Za-z0-9_]*)\s+([a-z][A-Za-z0-9_]*)\s*;', txt):
        d[m.group(1)].append(m.group(2))
    return d

# 1) DAO -> 表（support.super("g_x") + Mapper 裸 SQL 的 from/join/into/update）
dao_tables = collections.defaultdict(set)
for p in walk(os.path.join(BE, "dao")):
    cls = os.path.basename(p)[:-5]; txt = read(p)
    logic = cls[:-len("DynamicSqlSupport")] + "Mapper" if cls.endswith("DynamicSqlSupport") else cls
    for m in re.finditer(r'super\("([^"]+)"\)', txt): dao_tables[logic].add(m.group(1))
    for m in re.finditer(r'(?:from|into|update|join)\s+`?(g_[a-z_0-9]+)`?', txt, re.I): dao_tables[logic].add(m.group(1))
dao_tables = {k: v for k, v in dao_tables.items() if k.endswith("Mapper")}

# 2) service 单元：每个具体类/接口独立成单元（一接口多实现不合并，避免幻影并集）
services = {}
for p in walk(os.path.join(BE, "service")):
    cls = os.path.basename(p)[:-5]; txt = read(p)
    if "@Service" not in txt and not re.search(r'public\s+interface\s+' + re.escape(cls) + r'\b', txt): continue
    d = services.setdefault(cls, {"files": [], "flds": collections.defaultdict(list), "text": ""})
    d["files"].append(os.path.relpath(p, ROOT)); d["text"] += txt
    for t, ns in fields(txt).items(): d["flds"][t].extend(ns)
for extra in ("controller/third/api/DockerRegistryApiService.java", "controller/third/api/loki/LokiApiService.java"):
    p = os.path.join(BE, extra)
    if os.path.exists(p):
        cls = os.path.basename(p)[:-5]; txt = read(p)
        services.setdefault(cls, {"files": [], "flds": collections.defaultdict(list), "text": ""})
        services[cls]["files"].append(os.path.relpath(p, ROOT)); services[cls]["text"] += txt
        for t, ns in fields(txt).items(): services[cls]["flds"][t].extend(ns)
impl = {}
for p in walk(os.path.join(BE, "service")):
    cls = os.path.basename(p)[:-5]
    m = re.search(r'class\s+' + re.escape(cls) + r'\s+implements\s+([A-Za-z0-9_]+)', read(p))
    if m: impl[cls] = m.group(1)
impls_of = collections.defaultdict(set)
for c, i in impl.items(): impls_of[i].add(c)
for u, d in services.items():
    d["daos"] = sorted({t for t in d["flds"] if t in dao_tables})
    d["svcs"] = sorted({impl.get(t, t) for t in d["flds"]
                        if (t.endswith("Service") or t.endswith("ServiceImpl")) and impl.get(t, t) != u})

# 3) 方法级：service 方法名 -> 该方法直接访问的表
name2tabs = {}
for u, d in services.items():
    nt = {}
    for ln, body in methods(d["text"]):
        tabs = set()
        for dao, ns in d["flds"].items():
            if dao in dao_tables and any(re.search(r'\b'+re.escape(n)+r'\b', body) for n in ns): tabs |= dao_tables[dao]
        mm = re.search(r'([a-zA-Z][A-Za-z0-9_]*)\s*\(', body.split("\n")[0])
        if mm: nt.setdefault(mm.group(1), set()).update(tabs)
    name2tabs[u] = nt
for iface, ims in impls_of.items():                      # 接口方法 -> 其全部实现的并集
    if iface not in name2tabs: continue
    for im in ims:
        for k, v in name2tabs.get(im, {}).items(): name2tabs[iface].setdefault(k, set()).update(v)

# 4) controller -> 端点
cre = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\(([^)]*)\))?')
ctrls = {}
for p in walk(os.path.join(BE, "controller")):
    cls = os.path.basename(p)[:-5]; txt = read(p)
    if "@RestController" not in txt and "@Controller" not in txt: continue
    lines = txt.split("\n")
    cl = next((i for i, l in enumerate(lines) if re.search(r'public\s+(?:final\s+)?class\s+' + re.escape(cls) + r'\b', l)), None)
    base = ""
    if cl is not None:
        for j in range(cl-1, max(-1, cl-8), -1):
            m = re.search(r'@RequestMapping\(value=\{"([^"]*)"', lines[j])
            if m: base = m.group(1); break
    eps = []
    for i, ln in enumerate(lines):
        if cl is not None and i < cl: continue
        m = cre.search(ln)
        if not m: continue
        args = m.group(2) or ""
        vm = re.search(r'value=\{"([^"]*)"', args) or re.match(r'\s*"([^"]*)"', args)
        sub = vm.group(1) if vm else ""
        eps.append({"v": m.group(1).upper(), "url": (base.rstrip("/")+"/"+sub.lstrip("/")).rstrip("/") or base, "line": i+1})
    ctrls[cls] = {"txt": txt, "endpoints": eps, "flds": fields(txt)}

# 5) 端点 -> 表（精确 = 解析 field.method() 到 service 同名方法；上界 = 出现字段名即计该 service 全表集）
t2e, t2e_up, unres = collections.defaultdict(set), collections.defaultdict(set), 0
for c, d in ctrls.items():
    ms = methods(d["txt"])
    svc_f = {t: n for t, n in d["flds"].items() if t in services or t.endswith("Service")}
    dao_f = {t: n for t, n in d["flds"].items() if t.endswith("Mapper") and t != "ObjectMapper"}
    for e in d["endpoints"]:
        cand = [x for x in ms if x[0] <= e["line"]]
        body = cand[-1][1] if cand else ""
        tabs, up = set(), set()
        for dao, ns in dao_f.items():
            if any(re.search(r'\b'+re.escape(n)+r'\b', body) for n in ns):
                tabs |= dao_tables.get(dao, set()); up |= dao_tables.get(dao, set())
        for t, ns in svc_f.items():
            u = impl.get(t, t); hit = False
            for n in ns:
                if re.search(r'\b'+re.escape(n)+r'\.', body): hit = True
                for call in re.findall(re.escape(n)+r'\.([a-zA-Z][A-Za-z0-9_]*)\s*\(', body):
                    if call in name2tabs.get(t, {}) or call in name2tabs.get(u, {}):
                        tabs |= name2tabs.get(t, {}).get(call, set()) | name2tabs.get(u, {}).get(call, set())
                    else:
                        unres += 1
                        for x in {t, u} | set(impls_of.get(t, ())) | set(impls_of.get(u, ())):
                            for dao in services.get(x, {}).get("daos", []): tabs |= dao_tables.get(dao, set())
            if hit:
                for x in {t, u} | set(impls_of.get(t, ())) | set(impls_of.get(u, ())):
                    for dao in services.get(x, {}).get("daos", []): up |= dao_tables.get(dao, set())
        for t in tabs: t2e[t].add((e["v"], e["url"]))
        for t in up: t2e_up[t].add((e["v"], e["url"]))

# ---------- 输出 ----------
table_daos = collections.defaultdict(set)
for dao, ts in dao_tables.items():
    for t in ts: table_daos[t].add(dao)
t2s = collections.defaultdict(set)
for s, d in services.items():
    for dao in d["daos"]:
        for t in dao_tables.get(dao, []): t2s[t].add(s)
eps = sum(len(v["endpoints"]) for v in ctrls.values())
print(f"== 计数 == 逻辑DAO {len(dao_tables)} | 表 {len(table_daos)} | service 单元 {len(services)} "
      f"| controller {len(ctrls)} | 端点 {eps} | 触库端点 {len({(v,u) for t in t2e for v,u in t2e[t]})}")
print("\n== 表 | DAO | 承载 service（具体类） | 端点(精确) | 端点(上界) ==")
for t in sorted(table_daos):
    print(f"{t:24s} {','.join(sorted(table_daos[t])):52s} {','.join(sorted(t2s.get(t, []))):78s} "
          f"{len(t2e.get(t, [])):5d} {len(t2e_up.get(t, [])):5d}")
print(f"（未解析调用点 {unres} 个，已退化为该 service 全表集）")
print("\n== controller 直连 DAO（绕过 service 层）==")
for c, d in sorted(ctrls.items()):
    ds = [t for t in d["flds"] if t.endswith("Mapper") and t != "ObjectMapper"]
    if ds: print(f"{c:30s} {', '.join(sorted(ds))}")
print("\n== service -> service 注入 ==")
for s, d in sorted(services.items()):
    if d["svcs"]: print(f"{s:28s} -> {', '.join(d['svcs'])}")
```

### A.2 纯转发占比（§5.2 / §5.3）

```bash
BE=recovered/gaop-be/src/main/java/com/glriverside/gaop/be
python3 - <<'PY'
import os,re
BE="recovered/gaop-be/src/main/java/com/glriverside/gaop/be"
def methods(t):
    L=t.split("\n"); st=[i for i,l in enumerate(L) if re.match(r'^    (public|private|protected)\s',l)]
    return [(L[s],"\n".join(L[s:(st[k+1]-1 if k+1<len(st) else len(L))])) for k,s in enumerate(st)]
for dp,_,fs in os.walk(BE+"/controller"):
    for f in sorted(fs):
        if not f.endswith(".java"): continue
        t=open(os.path.join(dp,f),encoding="utf-8",errors="replace").read()
        if "@RestController" not in t and "@Controller" not in t: continue
        ms=methods(t); pure=0
        for sig,body in ms:
            st=[x.strip() for x in body.split("\n") if x.strip().endswith(";")]
            if len(st)<=1 and st and st[0].startswith("return"): pure+=1
        print(f"{f[:-5]:32s} methods={len(ms):4d} pureReturn={pure:4d}")
PY
```

### A.3 数据库级唯一约束（§4.0）

```bash
grep -rn 'UNIQUE' recovered/gaop-be/src/main/resources/db/migration/*.sql
# 期望输出 4 行：V0.7.4:9、V0.7.4:17、V0.7.7:9，以及 V0.8.6:3（V0.7.4 第一条的重复追加修正）
# 即实际约束 3 条
```

### A.4 死常量验证（§4.4 / §4.2）

```bash
BE=recovered/gaop-be/src/main/java/com/glriverside/gaop/be
for c in BackupType BackupSettingType SyncMethod RunningStatus AuthServiceType MonitorStatus; do
  printf '%-20s %s\n' "$c" "$(grep -rn "$c\." --include=*.java $BE | grep -v "constant/$c.java" | wc -l)"
done
# 期望：BackupType=0  BackupSettingType=0  SyncMethod=0  RunningStatus=0  AuthServiceType=25  MonitorStatus=51
```

### A.5 审批流是否存在（§4.12）

```bash
grep -rniE 'approve|approval|workflow|multistep' --include=*.java \
  recovered/gaop-be/src/main/java/com/glriverside/gaop/be | grep -v '/third/'
# 期望：无输出
```

---

## 附录 B · 术语与口径索引

| 术语 | 含义 |
|---|---|
| 战位 | `g_role`，源码与 DB 注释的原文用词（`g_role` 表注释「战位表」）；V0.5.0 起以 `type` 区分战位/角色 |
| 席位 / 子角色 | `g_sub_role` 系列表 |
| 软件 / 应用 | `g_software`，平台管理的业务应用 |
| 服务 | 两种含义：(1) k8s Service（编排对象）；(2) 软件下的一个 `service_name`（`g_service_port` 的维度）。本文件在歧义处显式标注 |
| 逻辑 DAO | `XxxDynamicSqlSupport` + `XxxMapper` 合并后的计数单元，共 40 个 |
| 触库端点 | 经方法级解析后命中 ≥1 张数据库表的端点。604 个端点中 **221** 个 |
| 哨兵值 | 「精确」= 方法级解析下界；「上界」= service 字段出现的粗投影 |
| 死常量 | 定义了但全仓引用 0 次的常量类（`BackupType` 等 4 个） |

---

## 附录 C · 本工单未做的事（明确边界）

1. **未做切分决定**：§3 的 12 个候选是**证据分组**，不是最终上下文清单；§6 的反例多数指向「该并」或「该拆」，本工单不裁决。
2. **未修改任何证据文件**：`recovered/`、`recovered-web/` 全文只读；本工单只写出本文件。
3. **未连任何集群、未连数据库、未联网**。
4. **未验证运行时行为**：所有结论均来自静态源码与迁移 SQL；任何"实际是否生效"的判断都标注为 `【推断】` 或「未定论」。
