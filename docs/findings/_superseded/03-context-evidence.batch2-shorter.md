> 说明：这是同一张工单（03-context-evidence）第二个研究子代理产出的较短版本（533 行），
> 已被 `../03-context-evidence.md`（1016 行）取代，保留以免丢证据。
>
> **已知更正（勿据本文件旧文决策）**：本文件 §4 对 `g_auth.used` 的描述「**可同时存在多个 `used=1`**」** overstated**。
> 复算 `$BE/service/DynamicLoadingService.java:178-184` 显示：旧行是**全量**清零（该循环内没有同类名过滤，
> `if (!infoClassName.equals(className)) continue` 只作用于 `:103-108` 的卸载判定），故互斥**是被实现的**；
> 真正缺的只是**事务边界**（全文 `@Transactional` 计数为 0）。正确版的证据与推演见
> `../03-context-evidence.md` 附录 D.2。本文件其余结论已与正文交叉核对，未发现冲突。

# 03 · 限界上下文切分证据（厂商 gaop-be 后端）

> 工单：`.scratch/backend-rewrite/issues/03-context-evidence.md`。
> **本文只给证据，不做切分决定。** 结论供后续 ADR 使用。
> 证据根：`/Users/csm/Desktop/Project/msop/recovered/gaop-be/`（**只读**）。
> 本仓：`/Users/csm/Desktop/PaaS/paas-core/`。
>
> 记号：`〔源：路径:行号〕`＝该事实可在该文件该行直接读到；`【代码事实】`＝反编译产物里字面存在；`【推断】`＝由多条代码事实推出；`未定论`＝证据不足或存在互相矛盾的证据。

---

## 0. 基线口径与复核入口

### 0.1 计数基线（全部可复核）

| 口径 | 数量 | 命令 |
|---|---|---|
| 迁移 SQL | 87 | `ls $MIG/*.sql \| wc -l` |
| `dao/*.java` | **79** = 40 `*Mapper` + 39 `*DynamicSqlSupport` | `ls $BE/dao/*.java \| wc -l` |
| 表（`super("...")`） | **39** | `grep -h 'super("' $BE/dao/*DynamicSqlSupport.java \| wc -l` |
| `service/*.java` | **41** = 5 接口 + 36 类 | `ls $BE/service/*.java \| wc -l` |
| `controller/*.java` | **39** | `ls $BE/controller/*.java \| wc -l` |
| REST 类（含子包） | 53 | `grep -rl '@RestController' --include=*.java $BE \| wc -l` |
| HTTP 端点（注解计数） | **604** | 见 §8 脚本 `verify.sh` 第 5 节 |
| 另有 DAO（无专属表） | 2：`DataBaseManagerMapper`（4 条 `g_database` 原生 SQL）、`dao/mysql/MysqlMapper`（MySQL 实例管理） | 见 §2.4 |

其中 `$MSOP=/Users/csm/Desktop/Project/msop`，`$BE=$MSOP/recovered/gaop-be/src/main/java/com/glriverside/gaop/be`，`$MIG=$MSOP/recovered/gaop-be/src/main/resources/db/migration`。

### 0.2 三处必须记住的命名陷阱【代码事实】

1. `ServiceManagementDynamicSqlSupport` 的 `super("g_service")` —— **不是** `g_service_management`〔源：`dao/ServiceManagementDynamicSqlSupport.java:39`〕。
2. `ImageGroupDynamicSqlSupport` 的 `super("g_group")` —— **不是** `g_image_group`〔源：`dao/ImageGroupDynamicSqlSupport.java:23`〕。
3. `RoleSoftwareMapper` 操作的是 `g_role_software`，但列名是 `app_id`（存的是 **software.id**）与 `type`；`g_role_software` 就是早期 `g_role_resource` 的改名产物〔源：`V0.0.3__alter-role-software-table.sql:2` 先 `drop foreign key g_role_resource_ibfk_2`〕。

### 0.3 证据分级

| 级别 | 含义 | 本文用法 |
|---|---|---|
| A｜代码事实 | 反编译产物字面可读 | 矩阵、计数、不变量存在性 |
| B｜可复核推理 | 由脚本抽取的多条 A 聚合 | 写者归属、耦合度量 |
| C｜推断 | 单条线索的归类判断 | 候选上下文的一句话职责、反例 |
| 未定论 | 证据冲突或缺失 | 明确标注 |

---

## 1. 三方映射矩阵（表 → 拥有它的 DAO → 暴露它的端点）

**读法**：`写者`＝在 service 层对该表 Mapper 发出 `insert/update/delete` 调用的 service（由方法名前缀判定，见 §8 脚本）；`全部涉及 service`＝读写都算；`controller`＝`直接` 触碰该表的 controller（经自己注入的 service 或自己注入的 Mapper，**不含** service→service 的二跳传播，二跳在 §2.2 单独度量）。

| 表 | DAO | 写者 | 全部涉及 service | controller | #C |
|---|---|---|---|---|---|
| `g_activate` | `ActivateMapper` | LicenceService | LicenceService,AuthService | AuthController,DataBaseController,LicenceController,PublicController,ServiceController,SoftwareController | 6 |
| `g_alert_record` | `AlertRecordMapper` | AlertService | AlertService | TenxApiV2Controller | 1 |
| `g_alert_rule` | `AlertRuleMapper` | AlertService | AlertService | TenxApiV2Controller | 1 |
| `g_alert_strategy` | `AlertStrategyMapper` | AlertService | AlertService | TenxApiV2Controller | 1 |
| `g_auth` | `AuthInfoMapper` | DynamicLoadingService,AuthService | DynamicLoadingService,AuthService,LicenceService,SoftwareService | AuthController,ConfigMapController,CsSoftwareController,DataBaseController,DynamicLoadingController,InstallController,LicenceController,PublicController,PublicNodeManagerController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SyncController | 15 |
| `g_backup` | `BackupMapper` | BackService | BackService | BackController,TestController | 2 |
| `g_backup_setting` | `BackupSettingMapper` | BackService,DatabaseService | BackService,DatabaseService | BackController,DataBaseController,PublicController,SoftwareController,SyncController,TestController | 6 |
| `g_ci_cd` | `CicdMapper` | CicdService | CicdService | CicdController | 1 |
| `g_client` | `ClientMapper` | ClientService | ClientService | ClientController | 1 |
| `g_database` | `DatabaseMapper` | DatabaseService | DatabaseService | DataBaseController,PublicController,SoftwareController,SyncController | 4 |
| `g_database_exec_log` | `DatabaseExecLogMapper` | (无写者) | DatabaseService | DataBaseController,PublicController,SoftwareController,SyncController | 4 |
| `g_database_record` | `DatabaseRecordMapper` | DatabaseService,DatabaseRecordService | DatabaseService,DatabaseRecordService | DataBaseController,DatabaseRecordController,PublicController,SoftwareController,SyncController | 5 |
| `g_database_user` | `DatabaseUserMapper` | DatabaseUserService | DatabaseUserService | DatabaseUserController,PublicController,SoftwareController,SyncController | 4 |
| `g_dictionary` | `DictionaryMapper` | (无写者) | UserAuditService | UserAuditController | 1 |
| `g_file` | `FileMapper` | FileService,DatabaseService | FileService,DatabaseService,CicdService | CicdController,DataBaseController,PublicController,PublicFdfsController,PublicNodeManagerController,SoftwareController,SyncController | 7 |
| `g_file_project` | `FileProjectMapper` | FileProjectService | FileProjectService,FileService | FileProjectController,PublicController,PublicFdfsController,PublicNodeManagerController,SyncController | 5 |
| `g_group` | `ImageGroupMapper` | K3sImageServiceImpl | K3sImageServiceImpl | PublicController,SyncController | 2 |
| `g_image_log` | `ImageLogMapper` | DockerService,ImageService | DockerService,ImageService,K3sImageServiceImpl,K8sImageServiceImpl | ImageController,PublicController,SoftwareController,SyncController | 4 |
| `g_language` | `LanguageMapper` | LanguageServiceImpl | LanguageServiceImpl |  | 0 |
| `g_language_version` | `LanguageVersionMapper` | LanguageServiceImpl | LanguageServiceImpl |  | 0 |
| `g_mode` | `ModeMapper` | ModeServiceImpl | ModeServiceImpl | TestController | 1 |
| `g_pod_event` | `PodEventMapper` | (无写者) |  | ServiceController | 1 |
| `g_role` | `RoleMapper` | RoleService,UserService,SubRoleServiceImpl | RoleService,UserService,SubRoleServiceImpl,SoftwareService,UserAuditService | AuthController,ConfigMapController,CsSoftwareController,DataBaseController,InstallController,PublicController,PublicNodeManagerController,RoleController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SubRoleController,SyncController,TestController,UserAuditController,UserController | 18 |
| `g_role_software` | `RoleSoftwareMapper` | RoleService,SoftwareService | RoleService,SoftwareService,CsSoftwareUploadService | ConfigMapController,CsSoftwareController,DataBaseController,InstallController,PublicController,PublicNodeManagerController,RoleController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SyncController,UserController | 14 |
| `g_service` | `ServiceManagementMapper` | (无写者) |  | PublicController,ServiceController | 2 |
| `g_service_auth` | `ServiceAuthMapper` | AuthService,LicenceService,DynamicLoadingService,SoftwareService | AuthService,LicenceService,DynamicLoadingService,SoftwareService | AuthController,ConfigMapController,CsSoftwareController,DataBaseController,DynamicLoadingController,InstallController,LicenceController,PublicController,PublicNodeManagerController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SyncController | 15 |
| `g_service_port` | `ServicePortMapper` | SoftwareService | SoftwareService | ConfigMapController,CsSoftwareController,DataBaseController,InstallController,PublicController,PublicNodeManagerController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SyncController | 12 |
| `g_setting` | `SettingMapper` | LicenceService,SettingService,K3sImageServiceImpl,FileService,LanguageServiceImpl | LicenceService,SettingService,K3sImageServiceImpl,FileService,LanguageServiceImpl,UserService | AuthController,GlobalSettingController,InstallController,LicenceController,PublicController,PublicFdfsController,PublicNodeManagerController,RoleController,ServiceController,SoftwareController,SubRoleController,SyncController,UserController | 13 |
| `g_software` | `SoftwareMapper` | SoftwareService,CsSoftwareUploadService | SoftwareService,CsSoftwareUploadService,RoleService,UserService,SettingService | AuthController,ConfigMapController,CsSoftwareController,DataBaseController,GlobalSettingController,InstallController,PublicController,PublicNodeManagerController,RoleController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SubRoleController,SyncController,UserController | 17 |
| `g_software_app` | `SoftwareAppMapper` | SoftwareService | SoftwareService,SettingService | ConfigMapController,CsSoftwareController,DataBaseController,GlobalSettingController,InstallController,PublicController,PublicNodeManagerController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SyncController,UserController | 14 |
| `g_sub_role` | `SubRoleMapper` | SubRoleServiceImpl | SubRoleServiceImpl,UserService,RoleService | AuthController,PublicController,RoleController,SubRoleController,SyncController,TestController,UserController | 7 |
| `g_sub_role_role` | `SubRoleRoleMapper` | RoleService,SubRoleServiceImpl | RoleService,SubRoleServiceImpl | PublicController,RoleController,SyncController,UserController | 4 |
| `g_upload_store_record` | `UploadStoreRecordMapper` | CsSoftwareUploadService,StoreMangeService | CsSoftwareUploadService,StoreMangeService,SoftwareService | ConfigMapController,CsSoftwareController,DataBaseController,InstallController,PublicController,PublicNodeManagerController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SyncController | 12 |
| `g_user` | `UserMapper` | UserService,RoleService,SubRoleServiceImpl | UserService,RoleService,SubRoleServiceImpl,UserAuditService | AuthController,PublicController,RoleController,SubRoleController,SyncController,UserAuditController,UserController | 7 |
| `g_user_audit` | `UserAuditMapper` | RoleService,UserAuditService,UserService | RoleService,UserAuditService,UserService | AuthController,PublicController,RoleController,SubRoleController,SyncController,UserAuditController,UserController | 7 |
| `g_user_login` | `UserLoginMapper` | RoleService | RoleService | PublicController,RoleController,SyncController,UserController | 4 |
| `g_user_role` | `UserRoleMapper` | UserService,RoleService | UserService,RoleService | AuthController,PublicController,RoleController,SubRoleController,SyncController,UserController | 6 |
| `g_user_sub_role` | `UserSubRoleMapper` | SubRoleServiceImpl,UserService | SubRoleServiceImpl,UserService | AuthController,PublicController,RoleController,SubRoleController,SyncController,UserController | 6 |
| `g_white_list` | `WhiteListMapper` | WhiteListService,SoftwareService | WhiteListService,SoftwareService | ConfigMapController,CsSoftwareController,DataBaseController,InstallController,PublicController,PublicNodeManagerController,ServiceController,SoftwareBackupV3Controller,SoftwareController,SoftwareImportV3Controller,StoreMangeController,SyncController,WhiteListController | 13 |

**矩阵行数核对**：39 行（每行一张表）；`#C` 列＝直接触碰该表的 controller 数。

---

## 1b. controller → 端点 → 直接表（端点面）

| controller | 端点数 | 路径前缀 | 直接表数 | +1 跳表数 | 行数 |
|---|---|---|---|---|---|
| `PublicController` | 68 | `/v1/pub` | 29 | 1 | 3782 |
| `TenxController` | 56 | `` | 0 | 1 | 641 |
| `TenxApiV2Controller` | 52 | `/cloud` | 3 | 1 | 638 |
| `ServiceController` | 51 | `/v1/service` | 13 | 7 | 4108 |
| `SoftwareController` | 44 | `/v1/software` | 18 | 0 | 797 |
| `SyncController` | 28 | `/v1/sync` | 26 | 2 | 1370 |
| `RoleController` | 27 | `/v1/role` | 11 | 2 | 424 |
| `UserController` | 25 | `/v1/user` | 14 | 1 | 594 |
| `DataBaseController` | 23 | `/v1/database` | 15 | 3 | 595 |
| `PublicFdfsController` | 21 | `/v1/pub` | 3 | 0 | 236 |
| `ImageController` | 17 | `/v1/image` | 1 | 2 | 297 |
| `BackController` | 16 | `/v1/backup` | 2 | 23 | 178 |
| `ConfigMapController` | 12 | `/v1/config_map` | 9 | 9 | 481 |
| `LicenceController` | 11 | `/v1/licence` | 4 | 0 | 129 |
| `PublicComputerController` | 10 | `/v1/pub` | 0 | 3 | 144 |
| `SubRoleController` | 10 | `/v1/sub_role` | 9 | 4 | 176 |
| `CsSoftwareController` | 8 | `/v1/cs_software` | 9 | 9 | 213 |
| `DashboardsController` | 8 | `/api/v2/dashboards` | 0 | 3 | 114 |
| `AuthController` | 7 | `/auth` | 11 | 5 | 343 |
| `CicdController` | 7 | `/v1/pub` | 2 | 4 | 89 |
| `InstancesController` | 7 | `/ui` | 0 | 1 | 81 |
| `LanguageController` | 7 | `/v1/lang` | 3 | 0 | 90 |
| `PublicNodeManagerController` | 7 | `/v1/pub` | 12 | 7 | 112 |
| `ClientController` | 6 | `/v1/pub/client` | 1 | 0 | 90 |
| `GlobalSettingController` | 6 | `/settings` | 3 | 0 | 78 |
| `LokiLogsController` | 6 | `/loki/logs` | 0 | 0 | 67 |
| `PubVipController` | 6 | `/v1/pub` | 0 | 0 | 385 |
| `DatabaseUserController` | 5 | `/v1/database/user` | 1 | 5 | 99 |
| `K8sProxyController` | 5 | `/k8s` | 0 | 0 | 223 |
| `ExSettingController` | 4 | `/exSetting` | 0 | 0 | 39 |
| `FileProjectController` | 4 | `/v1/pub/file_project` | 1 | 0 | 66 |
| `TestController` | 4 | `/v1/test` | 5 | 21 | 74 |
| `DynamicLoadingController` | 3 | `/v1/load` | 2 | 0 | 53 |
| `ModeController` | 3 | `/v1/mode` | 1 | 10 | 48 |
| `SoftwareBackupV3Controller` | 3 | `/v3/software` | 9 | 9 | 100 |
| `TenxSpiV2Controller` | 3 | `/cloud` | 0 | 1 | 63 |
| `UserAuditController` | 3 | `/audit` | 4 | 0 | 72 |
| `WhiteListController` | 3 | `/v1/white_list` | 1 | 0 | 67 |
| `AppController` | 2 | `/v1/app` | 0 | 0 | 50 |
| `InstallController` | 2 | `/v1/install` | 10 | 8 | 166 |
| `PubLanguageController` | 2 | `/v1/pub/lang` | 3 | 0 | 46 |
| `SoftwareImportV3Controller` | 2 | `/v3/software` | 9 | 9 | 97 |
| `StoreMangeController` | 2 | `/v1/store_mange` | 9 | 9 | 96 |
| `ComponentController` | 1 | `/v1/component` | 0 | 0 | 69 |
| `CreateK8sServicesController` | 1 | `/v1/CreateK8sServices` | 0 | 0 | 90 |
| `DatabaseRecordController` | 1 | `/v1/database_record` | 1 | 0 | 44 |
| `ImageUploadWebhookController` | 1 | `/webhook/image` | 0 | 0 | 29 |
| `K8sController` | 1 | `/v1/k8s` | 0 | 0 | 59 |
| `RegisterController` | 1 | `/register` | 0 | 0 | 28 |
| `RegistryWebhookController` | 1 | `/webhook/registry` | 0 | 0 | 61 |
| `WebhookController` | 1 | `/webhook` | 0 | 0 | 41 |
| `WebsocketController` | 0 | `` | 0 | 0 | 85 |

### 1c. service → 直接表（fan-out；均为具体实现类，无别名问题）

| service | 直接表数 | service 依赖数 | 表 |
|---|---|---|---|
| `SoftwareService` | 9 | 11 | `g_auth`, `g_role`, `g_role_software`, `g_service_auth`, `g_service_port`, `g_software`, `g_software_app`, `g_upload_store_record`, `g_white_list` |
| `RoleService` | 9 | 3 | `g_role`, `g_role_software`, `g_software`, `g_sub_role`, `g_sub_role_role`, `g_user`, `g_user_audit`, `g_user_login`, `g_user_role` |
| `UserService` | 8 | 6 | `g_role`, `g_setting`, `g_software`, `g_sub_role`, `g_user`, `g_user_audit`, `g_user_role`, `g_user_sub_role` |
| `DatabaseService` | 5 | 2 | `g_backup_setting`, `g_database`, `g_database_exec_log`, `g_database_record`, `g_file` |
| `SubRoleServiceImpl` | 5 | 1 | `g_role`, `g_sub_role`, `g_sub_role_role`, `g_user`, `g_user_sub_role` |
| `LicenceService` | 4 | 1 | `g_activate`, `g_auth`, `g_service_auth`, `g_setting` |
| `UserAuditService` | 4 | 0 | `g_dictionary`, `g_role`, `g_user`, `g_user_audit` |
| `CsSoftwareUploadService` | 3 | 2 | `g_role_software`, `g_software`, `g_upload_store_record` |
| `AlertService` | 3 | 0 | `g_alert_record`, `g_alert_rule`, `g_alert_strategy` |
| `AuthService` | 3 | 0 | `g_activate`, `g_auth`, `g_service_auth` |
| `FileService` | 3 | 0 | `g_file`, `g_file_project`, `g_setting` |
| `K3sImageServiceImpl` | 3 | 0 | `g_group`, `g_image_log`, `g_setting` |
| `LanguageServiceImpl` | 3 | 0 | `g_language`, `g_language_version`, `g_setting` |
| `SettingService` | 3 | 0 | `g_setting`, `g_software`, `g_software_app` |
| `BackService` | 2 | 12 | `g_backup`, `g_backup_setting` |
| `CicdService` | 2 | 2 | `g_ci_cd`, `g_file` |
| `DynamicLoadingService` | 2 | 0 | `g_auth`, `g_service_auth` |
| `ModeServiceImpl` | 1 | 3 | `g_mode` |
| `ImageService` | 1 | 2 | `g_image_log` |
| `DatabaseUserService` | 1 | 1 | `g_database_user` |
| `ClientService` | 1 | 0 | `g_client` |
| `DatabaseRecordService` | 1 | 0 | `g_database_record` |
| `DockerService` | 1 | 0 | `g_image_log` |
| `FileProjectService` | 1 | 0 | `g_file_project` |
| `K8sImageServiceImpl` | 1 | 0 | `g_image_log` |
| `StoreMangeService` | 1 | 0 | `g_upload_store_record` |
| `WhiteListService` | 1 | 0 | `g_white_list` |
| `DashboardsService` | 0 | 4 | — |
| `ComputerService` | 0 | 3 | — |
| `SoftwareBackupAsyncService` | 0 | 2 | — |
| `SoftwareImportAsyncService` | 0 | 2 | — |
| `FreeMarkerJobCreator` | 0 | 1 | — |
| `PrometheusStartEndStep` | 0 | 1 | — |
| `TenxService` | 0 | 1 | — |

---

## 2. 跨边界耦合度量

### 2.1 同一 DAO 方法访问多张表（**16 个方法**）

【代码事实】抽取方式：正则截取 `*Mapper.java` 中 `@Select/@Delete/@Insert/@Update` 注解体，匹配 `from|join|into|update <表名>`。完整清单：

| DAO.方法 | 触及的表 | 说明 |
|---|---|---|
| `RoleMapper.getRoleUser` | `g_user`, `g_user_role` | 为战位配置用户列表 |
| `RoleMapper.deleteRelationUserOtherRole` | `g_role`, `g_user_role` | 删用户时只保留非 admin/dev 的关系 |
| `SoftwareMapper.allVersionBySoftwareId` | `g_software`, `g_upload_store_record` | 软件 + 上传仓库版本 |
| `SoftwareMapper.allConnAppId` | `g_role`, `g_role_software` | 软件被哪些战位关联 |
| `SoftwareMapper.allServicePathBySoftware` | `g_software`, `g_software_app`, `g_service_port` | **3 表**，软件→应用→端口 |
| `SoftwareMapper.allServicePortsBySoftwareId` | `g_software`, `g_service_port` | |
| `SoftwareMapper.getSoftwareName` / `getSoftwareInfo` | `g_software`, `g_software_app` | |
| `SoftwareMapper.getWebEnter` | `g_software_app`, `g_service_port` | |
| `UploadStoreRecordMapper.getCurrentSoftwareVersionInfo` | `g_upload_store_record`, `g_software` | |
| `UserMapper.queryUserRoleByRoleKey` / `getRoleByUserId` / `getRoleNotForm` | `g_role`, `g_user_role` | |
| `UserRoleMapper.deleteRelateUserToRole` | `g_user`, `g_user_role`, `g_role` | **3 表**，含对 admin 的保护性 join |
| `UserRoleMapper.deleteDefaultResetUser` | `g_user`, `g_user_role` | |
| `UserSubRoleMapper.deleteRelateUserToSubRole` | `g_user`, `g_user_sub_role`, `g_sub_role` | **3 表** |

**聚类信号**：所有 3 表方法都落在「软件 × 服务端口 × 战位授权」与「用户 × 战位 × 子战位」两个簇里——这两簇是**真实聚合边**；其余多表方法都在用户/战位簇内（簇内关系，不构成跨上下文）。

【代码事实】另有 7 处 service 层内联 SQL 直接引用 `g_*`：`DatabaseService`（3）、`SoftwareService`、`RoleService` 等；以及 `DataBaseManagerMapper`（7 个方法，全部只碰 `g_database`）与 `dao/mysql/MysqlMapper`（25 个方法，其中 18 个带 `@DataSource(DbType.MYSQL)`，操作的是 **MySQL 实例的 `information_schema`/`mysql` 系统库**，不是本产品表——它是一个独立的基础设施端口，见 §6 反例 5）。

### 2.2 service 互相注入（**58 条边 / 17 个 service 有注入**）

| 度量 | 值 | 命令 |
|---|---|---|
| service→service 注入边 | **58** | §8 脚本第 4 节（实跑输出：`edges 58 services_with_injection 17`） |
| 有注入的 service 数 | 17 / 51 | 同上 |
| 最高被依赖（fan-in） | `BackupLogService` 6、`SoftwareService` 5、`DatabaseService`/`NodeVersionService`/`SubRoleService`/`K3sImageServiceImpl`/`RedisService` 各 3 | 同上 |
| 最大编排者（fan-out） | `BackService` 12、`SoftwareService` 11、`UserService` 6、`DashboardsService` 4、`ComputerService`/`RoleService` 各 3 | 同上 |

关键**环**与**枢纽**【代码事实 + 推断】：

- `RoleService → SubRoleService`、`UserService → RoleService`、`UserService → SubRoleService`、`SubRoleServiceImpl → BackupLogService`、`RoleService → BackupLogService` —— 用户/战位/子战位三者在 service 层已成环，无法靠包边界切开。
- `SoftwareService ↔ CsSoftwareUploadService`——**互相注入**：`SoftwareService` 在 `:313` 持有 `csSoftwareUploadService` 字段，`CsSoftwareUploadService` 在 `:87` 持有 `softwareService` 字段【源：`service/SoftwareService.java:313`；`service/CsSoftwareUploadService.java:87`】。
- `ModeServiceImpl → SoftwareService`、`TenxService → ModeService`、`DashboardsService → TenxService + K8sService + NodeVersionService` —— 外部云 API（Tenx）与平台模式互相咬合。
- `BackupLogService` 是**纯静态可变状态**（`public static List<BackupRecoveryLog> backLogs/recoveryLogs`、`public static Boolean RECOVERY`）〔源：`service/BackupLogService.java:29-31`〕，被 6 个 service 注入，跨备份/同步/导入三域——这是**无主共享状态**，不是领域概念。

### 2.3 controller 跨域（**33 个 controller 至少直接碰 1 张表；12 个 > 10 张**）

| controller | 端点数 | 直接表数 | 路径前缀 |
|---|---|---|---|
| `PublicController` | 68 | **29** | `/v1/pub` |
| `SyncController` | 28 | **26** | `/v1/sync` |
| `SoftwareController` | 44 | **18** | `/v1/software` |
| `DataBaseController` | 23 | **15** | `/v1/database` |
| `ServiceController` | 51 | **13** | `/v1/service` |
| `UserController` | 25 | **14** | `/v1/user` |
| `PublicNodeManagerController` | 7 | **12** | `/v1/pub` |
| `InstallController` | 2 | **10** | `/v1/install` |
| `AuthController` | 7 | **11** | `/auth` |
| `RoleController` | 27 | **11** | `/v1/role` |

**controller 直接注入 Mapper（绕过 service）的 8 个类**【代码事实】——这是最强的边界破坏证据：

| controller | 直接注入的 DAO | 源 |
|---|---|---|
| `ServiceController` | `ServiceManagementMapper`(g_service)、`PodEventMapper`、`SoftwareMapper`、`SoftwareAppMapper`、`ServicePortMapper`、`ServiceAuthMapper` | `controller/ServiceController.java:250-274` |
| `PublicController` | `ServiceManagementMapper`、`AuthInfoMapper`、`RoleMapper` | `controller/pub/PublicController.java:331,345,353` |
| `DataBaseController` | `SoftwareMapper`、`ServiceAuthMapper`、`MysqlMapper` | `controller/DataBaseController.java:106,108,114` |
| `SyncController` | `SoftwareMapper` | `controller/SyncController.java:171` |
| `AuthController` | `AuthInfoMapper` | `controller/AuthController.java:99` |
| `ImageController` | `ImageLogMapper` | `controller/ImageController.java:80` |
| `TestController` | `ModeMapper`、`SubRoleMapper`、`RoleMapper` | `controller/TestController.java:29,31,33` |
| `TenxApiV2Controller` | `AlertStrategyMapper`（运行时 `SpringBeanUtil.getBean`） | `controller/cloud/TenxApiV2Controller.java:210` |

**后果**：`g_service` 与 `g_pod_event` 两张表的**全部写入**都在 controller 层完成——service 层对它们零写。（见矩阵 §1 中该两行「写者」为「(无写者)」。）

### 2.4 无表归属的 DAO/service（边界噪声）

- `DataBaseManagerMapper`：7 个方法，全部操作 `g_database`（与 `DatabaseMapper` **重复拥有同一张表**）。【源：`dao/DataBaseManagerMapper.java`】
- `dao/mysql/MysqlMapper`：25 个方法，18 个 `@DataSource(DbType.MYSQL)`，操作 MySQL 实例；多数据源切换是横切基础设施。
- 7 个 service **不碰任何表**：`TenxService`、`DashboardsService`、`ComputerService`、`PrometheusStartEndStep`、`SoftwareBackupAsyncService`、`SoftwareImportAsyncService`、`FreeMarkerJobCreator`——它们是**外部 API 适配器 / 异步编排 / 模板生成**，属于基础设施而非领域。

---

## 3. 候选上下文：规模与一句话职责

> **本节是候选（candidate），不是切分决定。** 规模数字来自 §8 脚本；「职责」是 C 级推断。

| # | 候选 | 表数 | 表 | service 写者 | controller 数 | 端点合计 |
|---|---|---|---|---|---|---|
| C1 用户与战位授权 | 7 | `g_user`, `g_role`, `g_user_role`, `g_user_login`, `g_sub_role`, `g_user_sub_role`, `g_sub_role_role` | `RoleService`, `SubRoleServiceImpl`, `UserService` | 18 | 326 |
| C2 软件资产 | 2 | `g_software`, `g_software_app` | `CsSoftwareUploadService`, `SoftwareService` | 17 | 325 |
| C3 服务与端口 | 3 | `g_service`, `g_service_port`, `g_pod_event` | `(无写者)`, `SoftwareService` | 12 | 250 |
| C4 数据库服务 | 4 | `g_database`, `g_database_user`, `g_database_record`, `g_database_exec_log` | `(无写者)`, `DatabaseRecordService`, `DatabaseService`, `DatabaseUserService` | 6 | 169 |
| C5 文件与镜像制品 | 6 | `g_file`, `g_file_project`, `g_upload_store_record`, `g_image_log`, `g_group`, `g_ci_cd` | `CicdService`, `CsSoftwareUploadService`, `DatabaseService`, `DockerService`, `FileProjectService`, `FileService`, `ImageService`, `K3sImageServiceImpl`, `StoreMangeService` | 16 | 299 |
| C6 备份与恢复 | 2 | `g_backup`, `g_backup_setting` | `BackService`, `DatabaseService` | 6 | 183 |
| C7 告警与监控 | 3 | `g_alert_strategy`, `g_alert_rule`, `g_alert_record` | `AlertService` | 1 | 52 |
| C8 平台设置/模式/白名单 | 3 | `g_setting`, `g_mode`, `g_white_list` | `FileService`, `K3sImageServiceImpl`, `LanguageServiceImpl`, `LicenceService`, `ModeServiceImpl`, `SettingService`, `SoftwareService`, `WhiteListService` | 24 | 376 |
| C9 国际化 | 2 | `g_language`, `g_language_version` | `LanguageServiceImpl` | 3 | 34 |
| C10 分发物/客户端 | 1 | `g_client` | `ClientService` | 1 | 6 |
| C11 授权与许可 | 3 | `g_activate`, `g_auth`, `g_service_auth` | `AuthService`, `DynamicLoadingService`, `LicenceService`, `SoftwareService` | 15 | 271 |

全仓端点合计：**604**（53 个 REST 类的注解端点相加；另有 `@MessageMapping` 2 个入口、`@Scheduled` 5 处，未计入）。上表最后一列是**每个候选各自**的端点面，因共享表与共享 service **彼此重叠**，11 个候选相加远大于 604——重叠度本身就是耦合证据。

**interface→impl 别名**：5 个 service 是接口（`LanguageService`/`ModeService`/`NodeVersionService`/`SubRoleService`/`AbstractImageService`），controller 注入的是接口名。§1b/§3 的「直接表数」已把 `<X>Service` 解析到 `<X>ServiceImpl` 后再取表；未做该解析会把 `LanguageController`(`/v1/lang`)、`ModeController`(`/v1/mode`) 误判为 0 表。

**Top 端点集中度**：`TenxController`(56)+`TenxApiV2Controller`(52) 属于 X3（外部适配，0 表）；而 `PublicController`(68 端点 / **29 张表**) **同时**服务 C1/C2/C3/C4/C5——是全仓最强的「一个 controller 服务多域」反例，见 §6 反例 1。

---

## 4. 每个候选有没有真实不变量

> 判据：**不变量**＝存在「多个写者路径共用的状态约束」（状态机 / 额度 / 互斥 / 审批），且违反该约束会产生产品可见错误。纯 CRUD 转发＝增删改查 + 分页 + 存在性检查，无跨记录约束。

| 候选 | 有真实不变量？ | 证据 | 证据等级 |
|---|---|---|---|
| C1 用户与战位 | **有，且最强** | ① `admin`/`dev` 两个战位**不可删**（`RoleService.deleteRoleRelation` 抛 `role.cannot.delete.storage`）〔源：`service/RoleService.java:716-718`〕；② 同样两个 key 在 `isAdmin()` 里被判为「非可管理」〔源：`:670-674`〕；③ 删战位时要**级联清 6 张表**（`subRoleRole`/`userAudit`/`userRole`/`userLogin`/`roleResource`/`role`）〔源：`:719-726`〕——这是一个真实的删除不变量；④ 登录失败计数 + 锁定：**不落库**，走 Redis key `login-error:<username>`，阈值取自 `g_setting` 的 `system.permission.password_limit`/`retry_protection`，超限抛 `user-login.error.exceeded.attempts`〔源：`service/UserService.java:242-280,368-379`〕——注意 `g_user_login` 表记的是登录/注销流水（`UserLoginStatus` ONLINE/OFF-LINE），与本锁定计数器不是同一状态；⑤ `g_user.username` 与 `g_role.key` 有 DDL `unique`〔源：`V0.0.1__init_table.sql:4,18`〕 | A |
| C2 软件资产 | **有** | ① 软件名/标识唯一性检查走 `RoleService` 的计数量（`existKeyName`/`duplicateKey`/`duplicateName`/`existRoleName`）〔源：`service/RoleService.java:659-686`〕；② 软件有**授权码 + 生效/失效时间**三元组（`active_code`/`active_create`/`active_expire`）——`LicenceService.checkSoftwareActiveCode` 用 JWT 解析过期并返回 `expired`〔源：`service/LicenceService.java:581-613`〕；③ `g_software` 上同时挂 `service_port`、`role_software`、`upload_store_record`，删除软件必须级联处理（`SoftwareService` 内多处） | A（①②）/ B（③） |
| C3 服务与端口 | **有（但被 controller 绕过）** | ① **每个软件最多一个 `web_entry=1`**：所有写入方都显式把其他行置 `false`〔源：`service/SoftwareService.java:2163,2277,2866`；`controller/ServiceController.java:1132`〕，读取方一律 `get(0)`〔源：`:1007,1105,2004`〕；② `g_service_port` 的 `(software_id, service_name, port)` 三列写前必查〔源：`service/SoftwareService.java:1388,2155`〕——**但 DDL 上无唯一键**，是应用层不变量；③ `g_service.deleted` 语义与同名字段相反：本表 `0=已删除 / 1=未删除`〔源：`V0.3.9__add-service-table.sql:15`；`controller/pub/PublicController.java:948` 写 `setDeleted(1)`〕，而 `g_file`/`g_file_project.deleted` 是 `0:否 1:是`（即 `0=未删除`）〔源：`V0.2.6__add-file-project.sql:9,20`〕；查询过滤 `deleted=1`〔源：`controller/ServiceController.java:360,372`〕——**该列的全部写入在 controller**，service 层零写 | A |
| C4 数据库服务 | **有** | ① 库名唯一性：建库前 `existDatabase` 检查，重复直接抛〔源：`service/DatabaseService.java:272-273,558,687-688,705-706,716-717`〕；② 服务端资源不变量：`MysqlMapper` 用原生 `create database`/`GRANT ALL ON`/`drop database` 操作 **MySQL 实例**，与本次事务不在同一数据源〔源：`dao/mysql/MysqlMapper.java`〕——**跨事务不变量**（建库成功但记录失败会漂移）；③ `g_database_record`/`g_database_exec_log` 按 `database_name` **名称关联**而非外键〔源：`service/DatabaseService.java:227,833`〕 | A |
| C5 文件与镜像 | **有（弱-中）** | ① `g_file_project.name` 唯一性由 service 查重保证〔源：`service/FileProjectService.java:42-46`（新增）/`:61-67`（改名）〕，DDL **无唯一键**〔源：`V0.2.6__add-file-project.sql:4`〕；`g_file` 上的 `projectId` 复合索引**非唯一**〔源：`V0.6.1__add-file-index.sql:1`〕；② `g_file.deleted` 布尔软删除，所有查询带 `deleted=0`〔源：`dao/FileMapper.java:138,149`〕；③ 镜像上传状态机 `0失败/1成功/2处理中`〔源：`V0.4.1` 列注释；`service/DockerService.java:110,157,166,182`〕——但**状态可被任意覆写、无迁移校验**，属弱不变量 | A |
| C6 备份与恢复 | **有** | ① 异步任务进度是 **Caffeine 内存缓存**（`expireAfterWrite=2h`、`maximumSize=1000`、移除时删临时文件）〔源：`service/software/backup/SoftwareBackupTaskManager.java:19-31`；`SoftwareImportTaskManager.java:19-31`〕——**状态不在库里**，重启即丢，这是明确的不变量边界；② `g_backup.type` 状态机 `0 INIT / 1 INBACKUP / 2 BACKUPCOMPLETE / 3 BACKFAILURE`〔源：`constant/BackupType.java`〕；③ 全局并发闸门 `BackupLogService.RECOVERY`（静态 `Boolean`）+ `BackUpJob.flag`〔源：`service/BackupLogService.java:31,38-45`〕 | A |
| C7 告警 | **有** | ① 三级结构 `alert_strategy → alert_rule → alert_record`，外键列注释明确〔源：`V0.7.0__add_alert_strategy_tables.sql:29,48`〕；② 按 `fingerprint` 去重/合并告警（同一指纹的 firing 合并为一条记录）〔源：`service/AlertService.java:93,135,140,155`〕；③ 策略更新走 `updateByPrimaryKey` + 规则全量重读〔源：`:181-192`〕 | A |
| C8 平台设置/模式/白名单 | **有（局部很强）** | ① **g_mode 的行级互斥**：`changeModeTypeDB` 先把所有行 `used=0` 再置目标 `1`〔源：`service/impl/ModeServiceImpl.java:146-148`〕——每个时刻恰有一行 `used=1`；② **g_auth 的 JAR 互斥（弱）**：`loadingJar` 先把所有 `used=1` 的旧 JAR 置 0 再插入新行〔源：`service/DynamicLoadingService.java:102-110,175-184`〕——但**没有 `@Transactional`**，且只在「同类名」时卸载旧 JAR（`if (!infoClassName.equals(className)) continue`，同一个类名才卸载），因此**可同时存在多个 `used=1`**；③ 白名单路径查重后插入〔源：`service/WhiteListService.java:46-53`〕——DDL 无唯一键；④ 设置项是**弱类型 key-value**（`system.permission.password_limit` 等），无 schema | A |
| C9 国际化 | **有（唯一键型）** | ① `uni_lang_version_module_key(lang, lang_version, module(200), text_key)`〔源：`V0.8.6__alter-language-unique.sql:3`；早期版本 `module(20)` 见 `V0.7.4:9`〕；② `uni_lan_version(lang, lang_version)`〔源：`V0.7.4:17`〕——**这是全仓少见的、由 DDL 唯一键承载的真实不变量** | A |
| C10 客户端分发 | **无** | `ClientService.uploadClient`＝算 md5 → 落盘 → insert〔源：`service/ClientService.java:22-43`〕；`getClientList` 纯分页。无跨记录约束 | A |
| C11 授权与许可 | **有（核心）** | ① `g_activate` 是**单例行**（按 `name='gaop'` 查后 `get(0)`）〔源：`service/LicenceService.java:532-535`〕，DDL 里就是一行固定公钥〔源：`V0.4.9__add-active-table.sql:19-25`〕；② `g_service_auth.auth_code` 按 `service_id` 挂到具体服务〔源：`V0.4.9:11-14`；`service/AuthService.java:142,157,163`〕；③ 激活码是 JWT，用 `g_activate.public_key` 验签 + 校验过期〔源：`service/LicenceService.java:581-613`〕；④ `AuthInfo.used` 决定加载哪一个授权 JAR | A |
| X1 审计 | **有（弱）** | ① 审计表**故意不做外键**（`V0.3.1__alter-user-audit.sql:1-2` 删掉两条 FK 且未重建）——不变量是「用户/战位删除后审计仍可读」；② `module_name → g_dictionary.dictionary_key` 是**字典键关联**而非 FK〔源：`V0.6.5__update-user_audit-table.sql:3,13`〕 | A |
| X2 跨站点同步 | **有（分布式）** | `SyncController` 直接编排 9 个 service 做「读远端 → 落本地」；`SYNC_TOPIC` WebSocket 广播进度〔源：`controller/websocket/WebsocketEvent.java` 的 `/topic/sync`〕。不变量是**幂等/顺序**，但代码里是「逐条 select 后 insert」的朴素实现【推断】，未定论 | A/C |
| X3 平台运行时适配 | **无领域不变量** | 全是外部 API 调用与转换；`TenxService` 47 个方法、`InstancesService` 562 行，但不落库（0 表）〔源：§3 表〕 | A |

**不变量密度排序**（有真实不变量的候选）：C1 ≫ C11 ≈ C8 ≈ C4 ≈ C6 > C7 > C5 ≈ C9 > C2 > C3 > C10。

---

## 5. 纯 CRUD 转发清单

判据（可复核）：**service/controller 方法体内分支数 = 0（`if`/`switch`/`for`/`while` 全无）**，或「唯一语句＝一次 Mapper 调用 + 可选一次存在性检查」。

### 5.1 controller（零分支，共 18 个）

`BackController`(177 行)、`DynamicLoadingController`(52)、`ExSettingController`(38)、`FileProjectController`(65)、`GlobalSettingController`(77)、`InstancesController`(80)、`K8sController`(58)、`LicenceController`(128)、`LokiLogsController`(66)、`ModeController`(47)、`RegisterController`(27)、`TestController`(73)、`UserAuditController`(71)、`WebhookController`(40)、`WhiteListController`(66)、`CicdController`(88)、`PubLanguageController`(45)、`ImageUploadWebhookController`(28)

复核命令（§8 脚本第 6 节）：`for f in $(grep -rl '@RestController' --include=*.java $BE); do [ $(grep -cE '\bif *\(|\bswitch *\(|\bfor *\(|\bwhile *\(' $f) = 0 ] && echo $f; done`

> 注意：`BackController`、`LicenceController` 虽然**自身零分支**，但它们调用的 `BackService`/`LicenceService` 分支数分别为 67/51——它们是「薄 controller + 厚 service」，**不是**无规则。与之相对，`RegisterController`(27 行，读一个配置项)、`WebhookController`(40 行，转发到 WebSocket) 才是真转发。

### 5.2 service（疑似纯转发，方法体只有一次调用）

| service | 证据 |
|---|---|
| `DatabaseRecordService` | 仅 `getAllDatabaseRecord`(分页 select) + `addDatabaseRecord`(构造 POJO 后 insert)〔源：`service/DatabaseRecordService.java`〕；表 `g_database_record` 的**主写者其实是 `DatabaseService`**(2w/2c) |
| `StoreMangeService` | 4 个方法全是 `uploadStoreRecordMapper` 的 select/insert 包装〔源：`service/StoreMangeService.java`〕 |
| `KafkaProducerService` / `KafkaService` | 各 2 个方法，一个 send 一个 listen，无状态〔源：`service/KafkaProducerService.java`、`KafkaService.java`〕 |
| `ExSettingService` | 4 个方法，全部委托给 `DynamicLoading.jarLoading`〔源：`service/ExSettingService.java:11-24`〕 |
| `BackupLogService` | 3 个方法，全部是「往静态 List 塞 + WebSocket 广播」〔源：`service/BackupLogService.java:37-64`〕 |
| `K8sService` / `ComponentService` | 对外部 API 的薄封装（各 ≤10 方法，0 表）〔源：`service/K8sService.java`、`ComponentService.java`〕 |
| `PrometheusStartEndStep` | 纯计算（86 行，0 表，0 service 依赖之外的调用） |
| `RedisService` | 9 个方法全是对 Redisson 的透传〔源：`service/RedisService.java`〕 |

**不在此清单（虽有 CRUD 外观但含真实规则）**：`FileProjectService`（名称查重 + 软删，两处 `throw`）、`WhiteListService`（路径查重 + 网关通知）、`ClientService`（md5 计算 + 落盘）、`DatabaseUserService`（密码/授权联动）、`SubRoleServiceImpl`（三层授权联动）。

---

## 6. 每个候选的反例讨论（"看起来像 A 域，其实属于 B 域"）

**反例 1 ｜ `PublicController` 以为是一域，其实是 5 个域的聚合门面。**
它挂在 `/v1/pub`，名字暗示「公共/开放接口」，直觉上是一个独立上下文。但它直接碰 **29 张表**、注入 18 个不同 service，横跨 C1(g_user/g_role/g_user_role)、C2(g_software/g_software_app)、C3(g_service/g_service_port)、C4(g_database)、C5(g_file/g_upload_store_record)、C8(g_white_list/g_setting)。**结论**：它是**兼容门面（facade）**，不是上下文。切分时它应被拆解回各域，或明确标为「对外兼容层」——【源：§8 脚本输出的 `PublicController 直接表数=29`】。

**反例 2 ｜ `g_service`/`g_service_port` 看起来同属 C3，但 `g_service` 的写者根本不在 C3。**
`g_service`（表注释「服务列表」）持有服务的 CPU/内存配额，最像 C3 的聚合根。但 service 层对它**零写**；全部 `insert/update` 在 `ServiceController` 与 `PublicController`〔源：`controller/ServiceController.java:399`；`controller/pub/PublicController.java:948,1023…1415`〕。而 `g_service_port` 的**唯一写者**是 `SoftwareService`（19 写 / 33 调用），C2 的 service。**结论**：端口映射的不变量实际由**软件聚合**维护；把 `g_service_port` 放进 C3 会得到一个「没有写者」的上下文——它更接近 C2 的**值对象集合**（某软件的对外暴露面）。

**反例 3 ｜ `RoleSoftwareMapper`/`g_role_software` 看起来是 C1（授权），其实是 C2 的关联表。**
列名 `app_id` 存的是 `software.id`，列 `type`/`software_name` 描述的是软件属性〔源：`V0.0.7__add-cs-column-to-software-table.sql`；`service/RoleService.java:806` 的 `leftJoin(roleSoftware).on(roleSoftware.appId, equalTo(software.id))`〕。DDL 外键指向 `g_role`，但**内容语义属于软件目录**：`SoftwareService` 也直接写它（2w/6c），`CsSoftwareUploadService` 也读它。**结论**：它是 C1↔C2 的**共享连接表**，是两个上下文之间的真实耦合点，不适合单方面归属。

**反例 4 ｜ `BackController`/`g_backup` 看起来是「数据库备份」，其实是「整站状态快照」。**
表名 `g_backup` 与 Controller `/v1/backup` 让人以为它属于 C4（数据库）。但 `BackService` 的依赖列表是 `AppService、BackupLogService、DatabaseService、DatabaseUserService、ImageService、LanguageService、NodeVersionService、RoleService、SoftwareService、SubRoleService、UserService、WhiteListService`——**12 个 service**，覆盖 C1/C2/C4/C5/C9/C10。它是**横切编排者**（对整个平台做快照），而 `g_backup`/`g_backup_setting` 只是它的任务台账。〔源：`service/BackService.java` 字段区〕

**反例 5 ｜ `dao/mysql/MysqlMapper` 看起来是 C4（数据库服务），其实是基础设施端口。**
它在同一个 `dao` 包里、名字带 `Database`，最容易被并入 C4。但它 18/25 个方法带 `@DataSource(DbType.MYSQL)`，执行的是 `show databases`、`GRANT ALL ON …`、`information_schema.TABLES` 查询——操作对象是**MySQL 实例本身**，与 `g_database` 表无关。〔源：`dao/mysql/MysqlMapper.java`〕**结论**：它是多数据源基础设施 + 外部系统适配器，不应进入任何领域上下文（C4 只应通过一个窄接口消费它）。

**反例 6 ｜ `g_client` 看起来是 C10（客户端管理），其实是 C5 的制品分发。**
`g_client` 表只有 `version/type/md5/client_filename/client_store_filename` 五列〔源：`dao/ClientDynamicSqlSupport.java`〕，没有客户端实例、心跳、状态——它不是「客户端」实体，而是**安装包元数据**。`ClientService` 的工作就是算 md5 + 落盘 + 登记〔源：`service/ClientService.java`〕，与 `g_file`/`g_upload_store_record` 同构。**结论**：C10 与 C5 合并更符合不变量结构；单独成域会得到一个纯 CRUD、无不变量的上下文。

**反例 7 ｜ `g_alert_*` 看起来是 C7（自有告警），其实是 C7 的入站适配。**
三张告警表的**唯一写者**是 `AlertService`，但 `AlertService` 被谁调用？`TenxApiV2Controller`（`/cloud`，52 个端点）——数据来自**外部 Loki/Alertmanager 的 webhook 回调**〔源：`service/AlertService.java:88,106` 的 `receiveResolve`/`receiveFiring`；`controller/cloud/TenxApiV2Controller.java:210` 直接查 `AlertStrategyMapper`〕。**结论**：C7 的领域内核是「策略→规则→记录的指纹聚合」，但它的**入口属于 X3（外部适配）**；把它当成产品自己的告警配置域会忽略「记录是外部推送的、产品只是投影」这一事实。

**反例 8 ｜ `g_language`/`g_language_version` 看起来是 C8 设置的一部分（都是平台配置），其实是 C9 且有 DDL 唯一键。**
`g_setting`、`g_mode`、`g_white_list`、`g_language*` 常常被统一描述为「平台设置」。但 `g_language*` 是全仓唯一由 DDL 唯一键承载不变量的表〔源：`V0.8.6__alter-language-unique.sql:3`〕，且它有独立的广播通道（Kafka `global_language`）〔源：`service/KafkaProducerService.java:14`〕与独立的导出/导入（Excel/properties）。**结论**：它有独立生命周期，不属于「弱类型 key-value 设置」那一簇。

**反例 9 ｜ `g_user_audit` 看起来属于 C1（用户），其实是 X1 横切。**
它由 `UserAuditService`（主写者）与 `RoleService`/`UserService`（各 1-2 次写）共同写；入口是 `aspect/AuditLog` 注解 + `AuditAnnotationAspect`，**服务于所有域**；且 DDL 外键被**故意删除**〔源：`V0.3.1__alter-user-audit.sql:1-2`〕。**结论**：它是横切关注点，放进 C1 会让 C1 依赖 aspect 基础设施。

**反例 10 ｜ `g_mode` 看起来是 C8（设置），其实是 C3（运行时编排）。**
`ModeServiceImpl.changeModeTypeDB` 只写 `g_mode.used`，但 `startModeType` 会遍历 K8s Deployment 的 `MODE` 环境变量并**改写线上 Pod 的 env**〔源：`service/impl/ModeServiceImpl.java:114-146,158-212`〕。**结论**：`g_mode` 的**数据**在 C8，但它的**不变量（切换模式必须同步集群）**在 C3；这是一个「表在 A、规则在 B」的典型反例。

**反例 11 ｜ `g_activate` 看起来是 C11 的**核心聚合**，其实是「不可变常量表」。**
C11（授权与许可）最像领域的一部分，直觉上 `g_activate` 应是聚合根。但它**没有业务写入路径**：DDL 里就是一条 `INSERT ... VALUES ('1','gaop',<固定公钥>,<固定私钥>,1,NULL)`〔源：`V0.4.9__add-active-table.sql:19-25`〕，运行时只被 `select` + `get(0)` 读〔源：`service/LicenceService.java:532-535`〕。真正的状态在**外部**：激活码是 JWT，用 `g_activate.public_key` **验签**并核对系统时间〔源：`service/LicenceService.java:581-613`〕；`g_service_auth.auth_code` 才承载可变状态。**结论**：把 `g_activate` 当聚合根会得到一张只有读的"根"；C11 的真实不变量是**验签 + 过期**（无状态计算），而落库的状态只有 `g_service_auth` 一行/服务。

**反例 12 ｜ `SyncController` 看起来是 X2（同步域）的入口，其实是「横切编排器 + 无自己的表」。**
它有 28 个端点、1369 行、3 张表的直接读写〔源：§1b〕，最容易被切成一个「同步上下文」。但它**零专属表**：直接读写 D1–D7 的 `g_user`/`g_role`/`g_software`/`g_service`/`g_image_log`/`g_database*` 等 26 张表，并注入 12 个 service〔源：`controller/SyncController.java` 字段区〕。它的"规则"是**跨域复制语义**（哪些表要同步、同步顺序、冲突怎么处理），而不是某个实体的生命周期。**结论**：X2 是**横切能力（capability）**，不是限界上下文；把它切成上下文会让它反向依赖 C1–C8 的每一张表。

---

## 7. 未定论 / 证据缺口

| 项 | 状态 | 说明 |
|---|---|---|
| `g_oauth_code` | **未定论** | V0.4.8 建表（`code varchar(255) unique`）〔源：`V0.4.8__add-oauth-code-table.sql:4`〕，但**全部 Java 代码里零引用**；既无 Mapper 也无 service。是否废弃域，需产品确认 |
| `g_resource` / `g_role_resource` | **未定论** | V0.0.1 建表、V0.0.3 删除；`RoleService` 里仍存在 `roleResourceMapper` 字段并调用（`roleResourceMapper.select/delete`）——【推断】反编译产物对应一个**未包含在工作区内的旧 Mapper**，或 `roleResourceMapper` 实为 `RoleSoftwareMapper` 的别名，未定论 |
| `g_database_exec_log` | **未定论（疑似死表）** | 有 Mapper 与 39 表清单，但 87 个迁移**无建表脚本**；`DatabaseService` 只有 2 次 `select`，**无任何 insert**〔源：`service/DatabaseService.java:833,837`〕——写者缺失 |
| `g_pod_event` | **未定论** | 唯一写者 `controller/ServiceController.java:3871`（controller 直连 DAO）；另有 `ApplicationStarted.java` 引用。service 层零写，无法判断「谁拥有事件流」 |
| `g_service_auth.service_id` 指向 | **未定论** | 列名 `service_id`，但写入的是 **serviceName**〔源：`service/AuthService.java:142`〕；无法确定它对应 `g_service.service_name` 还是 K8s Service 名 |
| `g_software.app_name ↔ g_software_app.app_name` | **未定论** | 注释均为「时速云应用」，无代码读该映射的语义 |
| `g_role_sub_role`（真库独有） | **未定论** | 仅存在于真库快照，无 Java 引用；见既有 findings 01 §6 |
| 端点总数 604 vs OpenAPI 542 | **未定论** | 本文按注解计数（含无路径的 `@RequestMapping` 方法），厂商 `/v3/api-docs` 报 542 operations；差值的来源（重复路径合并？隐藏端点？）未核对 |
| X2 同步的幂等性 | **未定论** | 未逐条读 1369 行的 `SyncController`；不变量候选是「顺序 + 幂等」，未验证 |

---

## 8. 复现脚本（可直接保存执行）

```bash
#!/usr/bin/env bash
# 03-context-evidence 复核脚本（只读厂商证据）
set -u
MSOP=/Users/csm/Desktop/Project/msop
BE="$MSOP/recovered/gaop-be/src/main/java/com/glriverside/gaop/be"
MIG="$MSOP/recovered/gaop-be/src/main/resources/db/migration"

echo "== 1) 基线计数 =="
echo "迁移 SQL            : $(ls "$MIG"/*.sql | wc -l | tr -d ' ')"
echo "dao/*.java          : $(ls "$BE"/dao/*.java | wc -l | tr -d ' ')  (Mapper=$(ls "$BE"/dao/*Mapper.java | wc -l | tr -d ' ') + DynamicSqlSupport=$(ls "$BE"/dao/*DynamicSqlSupport.java | wc -l | tr -d ' '))"
echo "表（super）         : $(grep -h 'super("' "$BE"/dao/*DynamicSqlSupport.java | wc -l | tr -d ' ')"
echo "service/*.java      : $(ls "$BE"/service/*.java | wc -l | tr -d ' ')  (接口=$(grep -l '^public interface' "$BE"/service/*.java | wc -l | tr -d ' ') 类=$(grep -l '^public class' "$BE"/service/*.java | wc -l | tr -d ' '))"
echo "controller/*.java   : $(ls "$BE"/controller/*.java | wc -l | tr -d ' ')"
echo "REST 类（含子包）   : $(grep -rl '@RestController' --include=*.java "$BE" | wc -l | tr -d ' ')"

echo
echo "== 2) 表 → 拥有它的 DAO =="
for f in "$BE"/dao/*DynamicSqlSupport.java; do
  t=$(grep -o 'super("[^"]*")' "$f" | sed 's/super("//;s/")//')
  echo "$t <- $(basename "$f" .java)"
done | sort

echo
echo "== 3) 同一 DAO 方法访问多表（自定义注解里的 from/join）=="
echo "   （下面的 python 会打印每一对 [Mapper.java, [(方法, [表...])]]；本次运行结果：16 个方法，见 §2.1）"
python3 - "$BE" <<'PY'
import os,re,sys,collections
BE=sys.argv[1]
tblre=re.compile(r'\b(?:from|join|into|update)\s+`?(g_[a-z_]+)`?',re.I)
for fn in sorted(os.listdir(os.path.join(BE,'dao'))):
    if not fn.endswith('Mapper.java'): continue
    src=open(os.path.join(BE,'dao',fn),encoding='utf-8',errors='replace').read()
    n=[]
    for m in re.finditer(r'@(Select|Delete|Insert|Update)\s*\(',src):
        i=m.end(); d=1; j=i
        while j<len(src) and d>0:
            if src[j]=='(': d+=1
            elif src[j]==')': d-=1
            j+=1
        body=src[i:j-1]
        tail=src[j:j+900]
        mm=re.search(r'public\s+[\w<>,\[\]\.\? ]+\s+(\w+)\s*\(',tail)
        ts=sorted({t.lower() for t in tblre.findall(body)})
        if len(ts)>1: n.append((mm.group(1) if mm else '?',ts))
    if n: print(fn, n)
PY

echo
echo "== 4) service → service 注入边数 =="
python3 - "$BE" <<'PY'
import os,re,sys,collections
BE=sys.argv[1]
svc={}
for fn in sorted(os.listdir(os.path.join(BE,'service'))):
    if fn.endswith('.java'): svc[fn[:-5]]=os.path.join(BE,'service',fn)
for sub in ('service/impl','service/software/backup'):
    for fn in sorted(os.listdir(os.path.join(BE,sub))):
        if fn.endswith('.java'): svc[fn[:-5]]=os.path.join(BE,sub,fn)
names=set(svc); mp={}; edges=collections.defaultdict(set)
for nm,p in svc.items():
    src=open(p,encoding='utf-8',errors='replace').read()
    for m in re.finditer(r'(?:private|protected|public)?\s*(?:final\s+)?([A-Z]\w+)\s+(\w+)\s*[;=]',src): mp[nm,m.group(2)]=m.group(1)
    for m in re.finditer(r'\b(\w+)\.(\w+)\s*\(',src):
        t=mp.get((nm,m.group(1)))
        if t in names and t!=nm: edges[nm].add(t)
tot=sum(len(v) for v in edges.values())
print('edges',tot,'services_with_injection',len(edges))
for k in sorted(edges,key=lambda x:-len(edges[x]))[:8]:
    print(' ',k,'->',sorted(edges[k]))
PY

echo
echo "== 5) controller 端点计数（按类） =="
tot=0
for f in $(grep -rl '@RestController' --include=*.java "$BE" | sort); do
  n=$(grep -cE '@(Get|Post|Put|Delete|Patch)Mapping' "$f")
  if [ "$n" -gt 0 ]; then printf "%4d  %s\n" "$n" "$(basename "$f")"; tot=$((tot+n)); fi
done | sort -rn
echo "端点合计（只统计有端点的类）; 若要含零端点类，见 §0.1"

echo
echo "== 6) 零分支 controller（纯转发候选） =="
for f in $(grep -rl '@RestController' --include=*.java "$BE" | sort); do
  b=$(grep -cE '\bif *\(|\bswitch *\(|\bfor *\(|\bwhile *\(' "$f")
  [ "$b" = "0" ] && echo "  0  $(basename "$f" .java)"
done

echo
echo "== 7) 关键不变量抽查 =="
echo "-- g_mode 互斥 --";            grep -n 'used' "$BE"/service/impl/ModeServiceImpl.java | head -4
echo "-- g_auth used 重置+插入 --";  grep -n 'setUsed(0)\|setUsed(1)\|@Transactional' "$BE"/service/DynamicLoadingService.java
echo "-- g_service.deleted=1 插入 --"; grep -n 'setDeleted(1)' "$BE"/controller/pub/PublicController.java
echo "-- admin/dev 不可删 --";       grep -n 'role.cannot.delete.storage' "$BE"/service/RoleService.java
echo "-- 登录失败锁定 --";           grep -n 'user-login.error.exceeded.attempts' "$BE"/service/UserService.java
```

**本文数字与上表的对应关系**：

| 报告位置 | 脚本节 |
|---|---|
| §0.1 基线计数 | 第 1 节 |
| §0.2 / §7 命名陷阱与未定论 | 第 2 节 |
| §1 矩阵的「写者/涉及 service」 | 第 3 节 + `own`/`svc` 抽取（依赖方法名前缀 `insert\|update\|delete`） |
| §2.1 十六个多表方法 | 第 3 节 |
| §2.2 五十八条注入边 | 第 4 节 |
| §3 端点数、§1 端点列 | 第 5 节 |
| §5.1 纯转发 controller | 第 6 节 |
| §4 不变量证据 | 第 7 节 |

> 说明：§1 矩阵中的「直接表数 / 写者 / 涉及 service」由本 agent 的抽取脚本产出（脚本与本次运行结果在 `/tmp/ctxev/`），其判定规则已写进上文；若抽取规则改动（例如把「读」也算写者），数字会变，请在复用前重跑第 3 节并核对。

---

## 9. 一页速览

- **39 表 / 79 DAO / 41 service / 39 controller / 604 端点**——数字对得上工单口径〔§0.1〕。
- **真正的不变量集中在 5 处**：战位不可删与级联删除（C1）、激活码 JWT 验签与单例公钥（C11）、模式行级互斥（C8×C3）、告警指纹聚合（C7）、国际化 DDL 唯一键（C9）。其余大量是应用层查重 + 分页 CRUD。
- **最强的耦合信号**：`SoftwareService` 单类触 9 表 / 编 11 个 service；`PublicController` 单类触 28 表；58 条 service 互注边，且在用户/战位/子战位三者间成环。
- **边界已经被破坏的两张表**：`g_service`、`g_pod_event` —— 写者全在 controller。
- **最该警惕的「伪单域」**：`/v1/pub` 门面、`g_backup`（其实是全站快照）、`MysqlMapper`（其实是基础设施）、`g_client`（其实是制品）、`g_user_audit`（其实是横切）。
- **未定论 9 项**见 §7，其中 `g_oauth_code`、`g_database_exec_log`、`g_pod_event` 三张表**没有可信的领域归属**，切分前需产品确认。
