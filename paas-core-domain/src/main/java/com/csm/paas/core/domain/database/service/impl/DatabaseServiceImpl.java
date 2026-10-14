package com.csm.paas.core.domain.database.service.impl;

import com.csm.paas.core.domain.database.adapter.port.IDatabaseProcessPort;
import com.csm.paas.core.domain.database.adapter.repository.IDatabaseRepository;
import com.csm.paas.core.domain.database.model.entity.BusinessDatabaseNameEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseExecResultEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseImportInfoEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseImportSqlInfoEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseItemEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseListEntity;
import com.csm.paas.core.domain.database.model.entity.DatabasePermissionEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseRecordEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseSyncStatusEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseTableSizeEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseTableSizeItemEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserRelationInfoEntity;
import com.csm.paas.core.domain.database.model.entity.MysqlGlobalSettingsEntity;
import com.csm.paas.core.domain.database.model.valobj.DatabaseDescribeVO;
import com.csm.paas.core.domain.database.service.IDatabaseService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.csm.paas.core.types.exception.SystemException;

/**
 * `/v1/database` 的业务实现——逐条照抄厂商 {@code service/DataBaseService.java}。
 *
 * <h3>照抄的怪点（不要"顺手修好"）</h3>
 * <ol>
 *   <li>`executeSQLFromImport:510-552`：`catch (Exception e)` 里 `result.success(false)...build()` 的返回值
 *       **被丢弃**，方法最后无条件 `return ...success(true).build()` ⇒ 这个方法**永远报成功**（除非 SQLException 分支）。</li>
 *   <li>`getAllMysqlGlobalSettings:844-855`：`storageSize` 的 TB 分支用的是**已被覆盖成 GB 的 `mysqlSize`**，
 *       而不是再除一次；且三段判断互不 `else`，会顺序覆盖。</li>
 *   <li>`deleteDatabaseAllByName:713-731`：逐个库处理，遇到不存在的库**直接抛 400**，
 *       此时前面的库**已经删掉了**（没有事务回滚）。</li>
 *   <li>`getSnapShotStatus:907`：`updatedAt.equals(time)` —— `time` 来自 map，**可能为 null**（NPE 风险照抄）。</li>
 *   <li>`saveDataBaseSnapShot` 有两大段几乎逐行重复的 if/else（`fileList` 非空 / 空），照抄保留。</li>
 *   <li>`zipDataBaseFile:1224` 用的是 `getFileListByBackUpByInitSqlFile`（**初始化 sql**），
 *       也就是说 `g_backup_setting.type` 在一键快照路径里**根本没用上**。</li>
 * </ol>
 *
 * <h3>本实现的"缺的一段"（详见回报）</h3>
 * <ul>
 *   <li>`databaseUser.json`：需要 `g_database_user` 表 + `DatabaseUserService`（本切片未建模）⇒ 该条目被跳过
 *       （厂商在列表为空时也跳过，形状一致）。</li>
 *   <li>`importDataBase` 末尾的 `tenxCloud.restartService(clusterID, allServiceName)` 与全部
 *       STOMP 进度推送（`/topic/database`、`/topic/sync`、`/topic/saveDatabase`、`/topic/common`）**未接**。</li>
 *   <li>`getFileListByBackUpSetting:2255-2376`（按 `g_backup_setting.type` 决定 `--no-data`）仅被厂商的
 *       非 HTTP 定时任务用到，本切片未移植。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseServiceImpl implements IDatabaseService {

    /** 厂商 `DatabaseService.importStatus`（`public static final DbImportStatus importStatus`，行 142）。 */
    public static final DatabaseSyncStatusEntity importStatus = DatabaseSyncStatusEntity.init();

    /** 厂商 `DatabaseService.saveDatabaseSnapshot`（行 144，实例级 HashMap）。 */
    private final Map<String, Long> saveDatabaseSnapshot = new HashMap<>();

    /** 厂商 `DatabaseService.saveDatabaseStatus`（行 143）。 */
    public static volatile int saveDatabaseStatus = 0;

    private final IDatabaseRepository databaseRepository;
    private final IDatabaseProcessPort databaseProcessPort;

    @Value("${data.backup:/opt/gaop/upload/backup}")
    private String fileBackUp;

    @Value("${cicd.projectid:31fceaa9-0910-47fd-80bf-730f00a6933b}")
    private String cicdProjectId;

    // ================================================================ 读

    /** 厂商 `getUserTableCount:604-617`。 */
    @Override
    public DatabaseListEntity getUserTableCount(int page, int size, String keyword) {
        List<BusinessDatabaseNameEntity> rows = this.databaseRepository.allDatabasesTableCount(keyword);
        // 厂商用 PageHelper：返回**分页切片**，total 是**过滤后的全量行数**
        int offset = PageUtils.offset(page, size);
        List<BusinessDatabaseNameEntity> pageRows = rows.stream()
                .skip(offset).limit(size).toList();
        List<DatabaseItemEntity> items = pageRows.stream()
                .map(r -> DatabaseItemEntity.builder().name(r.getName()).tableCount(r.getTableCount()).build())
                .collect(Collectors.toCollection(ArrayList::new));
        List<String> dbNames = rows.stream().map(BusinessDatabaseNameEntity::getName).toList();
        if (!dbNames.isEmpty()) {
            List<DatabaseEntity> list = this.databaseRepository.queryAllDatabaseRows().stream()
                    .filter(d -> dbNames.contains(d.getDatabaseName())).toList();
            mapDatabaseDesc(items, list);
        }
        return DatabaseListEntity.builder().databaseItemList(items).total((long) rows.size()).build();
    }

    /** 厂商 `mapDatabaseDesc:655-681`：只在库里有**非 null** 值时才覆盖。 */
    private void mapDatabaseDesc(List<DatabaseItemEntity> databaseItems, List<DatabaseEntity> list) {
        if (databaseItems == null || list == null) {
            return;
        }
        Map<String, String> serviceMap = list.stream().filter(e -> e.getService() != null)
                .collect(Collectors.toMap(DatabaseEntity::getDatabaseName, DatabaseEntity::getService, (a, b) -> a));
        Map<String, String> descMap = list.stream().filter(e -> e.getDesc() != null)
                .collect(Collectors.toMap(DatabaseEntity::getDatabaseName, DatabaseEntity::getDesc, (a, b) -> a));
        Map<String, String> sqlMap = list.stream().filter(e -> e.getSqlName() != null)
                .collect(Collectors.toMap(DatabaseEntity::getDatabaseName, DatabaseEntity::getSqlName, (a, b) -> a));
        Map<String, String> lastUpdateUserMap = list.stream().filter(e -> e.getUsername() != null)
                .collect(Collectors.toMap(DatabaseEntity::getDatabaseName, DatabaseEntity::getUsername, (a, b) -> a));
        databaseItems.forEach(item -> {
            String service = serviceMap.get(item.getName());
            if (service != null) {
                item.setService(service);
            }
            String desc = descMap.get(item.getName());
            if (desc != null) {
                item.setDesc(desc);
            }
            if (sqlMap.containsKey(item.getName())) {
                item.setSqlName(sqlMap.get(item.getName()));
            }
            if (lastUpdateUserMap.containsKey(item.getName())) {
                item.setUserName(lastUpdateUserMap.get(item.getName()));
            }
        });
    }

    /** 厂商 `getAccessControl:683-696`。 */
    @Override
    public List<DatabasePermissionEntity> getAccessControl(String databaseName, String accessUser) {
        List<String> databases = this.databaseRepository.showDatabases();
        String user = this.databaseRepository.queryAccessUser(accessUser);
        if (!databases.stream().anyMatch(s -> s.equals(databaseName))) {
            throw new WrongArgumentException("database.not.found", databaseName);
        }
        if (user == null) {
            throw new WrongArgumentException("user.not.found");
        }
        return this.databaseRepository.databasesAccessControl(databaseName, accessUser);
    }

    /** 厂商 `getDatabaseBelongUsers:698-701`。 */
    @Override
    public List<String> getDatabaseBelongUsers(String databaseName) {
        return this.databaseRepository.databaseBelongUsers(databaseName);
    }

    /** 厂商 `getAllMysqlGlobalSettings:841-870`。 */
    @Override
    public MysqlGlobalSettingsEntity getAllMysqlGlobalSettings() {
        Map<String, String> map = this.databaseRepository.allVariables();
        String storageSize;
        int size = this.databaseRepository.getStorageSizeMb();
        if (size <= 1024) {
            storageSize = size + " MB";
        } else {
            int mysqlSize = size / 1024;
            storageSize = mysqlSize + " GB";
            if (mysqlSize >= 1024) {
                int settingSize = mysqlSize / 1024;
                storageSize = settingSize + " TB";
            }
        }
        return MysqlGlobalSettingsEntity.builder()
                .storageEngine(getFromMap(map, "default_storage_engine"))
                .storageSize(storageSize)
                .timeZone(getFromMap(map, "time_zone"))
                .caseSensitive(getFromMap(map, "lower_case_table_names"))
                .port(getFromMap(map, "port"))
                .version(getFromMap(map, "innodb_version"))
                .createdAt(this.databaseRepository.getMysqlCreatedAt())
                .build();
    }

    /** 厂商 `getFromMap:872-878`：取不到返回**空串**。 */
    private String getFromMap(Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null ? value : "";
    }

    /** 厂商 `getBusinessDataBaseTableSize:2438-2462`。 */
    @Override
    public List<DatabaseTableSizeEntity> getBusinessDataBaseTableSize() {
        List<DatabaseTableSizeItemEntity> items = this.databaseRepository.getDbTableSize();
        Map<String, List<DatabaseTableSizeItemEntity>> tableMap = items.stream()
                .collect(Collectors.groupingBy(DatabaseTableSizeItemEntity::getDatabaseName));
        List<DatabaseTableSizeEntity> result = new ArrayList<>(tableMap.size());
        for (Map.Entry<String, List<DatabaseTableSizeItemEntity>> entry : tableMap.entrySet()) {
            List<DatabaseTableSizeItemEntity> tableSizeItems = entry.getValue();
            List<DatabaseTableSizeEntity> children = new ArrayList<>(tableSizeItems.size());
            BigDecimal databaseSize = new BigDecimal(0);
            for (DatabaseTableSizeItemEntity item : tableSizeItems) {
                DatabaseTableSizeEntity tableEntity = DatabaseTableSizeEntity.builder()
                        .size(item.getSize().setScale(2, RoundingMode.HALF_UP).doubleValue())
                        .name(item.getTableName())
                        .build();
                databaseSize = databaseSize.add(item.getSize());
                children.add(tableEntity);
            }
            result.add(DatabaseTableSizeEntity.builder()
                    .name(entry.getKey())
                    .size(databaseSize.setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .children(children)
                    .build());
        }
        result.sort(Comparator.comparing(DatabaseTableSizeEntity::getSize).reversed());
        return result;
    }

    /** 厂商 `getRelationDataBaseList:2378-2396`。 */
    @Override
    public List<DatabaseUserRelationInfoEntity> getRelationDataBaseList() {
        List<DatabaseUserRelationInfoEntity> result = new ArrayList<>();
        List<BusinessDatabaseNameEntity> databaseItems = this.databaseRepository.allDatabasesTableCount(null);
        List<String> dbNames = databaseItems.stream().map(BusinessDatabaseNameEntity::getName).toList();
        if (!dbNames.isEmpty()) {
            for (String dbName : dbNames) {
                List<String> belongUsers = this.databaseRepository.databaseBelongUsers(dbName);
                if (belongUsers.isEmpty()) {
                    continue;
                }
                result.add(DatabaseUserRelationInfoEntity.builder()
                        .databaseName(dbName).databaseUserList(belongUsers).build());
            }
        } else {
            log.info("业务数据库列表不存在数据库，不进行导出数据库用户关联数据库的操作");
        }
        return result;
    }

    /** 厂商 `getInitSqlName:2424-2432`。 */
    @Override
    public String getInitSqlName(String databaseName) {
        return this.databaseRepository.queryDatabaseByName(databaseName)
                .map(DatabaseEntity::getSqlName).orElse(null);
    }

    /** 厂商 `existDatabase:558-560`。 */
    @Override
    public boolean existDatabase(String databaseName) {
        return this.databaseRepository.showDatabases().stream().anyMatch(s -> s.equals(databaseName));
    }

    /** 厂商 `getDataBaseBackUp:2084-2092`。 */
    @Override
    public InputStream getDataBaseBackUp() throws IOException {
        File baseFile = new File(this.fileBackUp, "dataBase_backup.zip");
        if (baseFile.exists()) {
            return Files.newInputStream(baseFile.toPath());
        }
        throw new WrongArgumentException("database.no.db.snapshot.backup");
    }

    /** 厂商 `getSnapShotStatus:889-920`。 */
    @Override
    public Integer getSnapShotStatus() {
        String dataBaseFileName = "dataBase_backup.zip";
        String snapShotStatusKey = "snapShotStatus";
        if (!this.saveDatabaseSnapshot.isEmpty()) {
            log.info("正处于保存快照状态");
            Long time = this.saveDatabaseSnapshot.get(snapShotStatusKey);
            long currentTimeMillis = System.currentTimeMillis();
            // 照抄：`time` 可能为 null ⇒ NPE（厂商 :897 就是直接相减）
            boolean saveTime = currentTimeMillis - time > 1800000L;
            if (saveTime) {
                log.info("调用数据库快照保存花费时间太长，默认已经保存完成");
                this.saveDatabaseSnapshot.remove(snapShotStatusKey);
                return 1;
            }
            if (this.databaseRepository.countFiles(this.cicdProjectId, dataBaseFileName) != 0) {
                Long updatedAt = this.databaseRepository.queryFileUpdatedAt(this.cicdProjectId, dataBaseFileName);
                if (updatedAt != null && updatedAt.equals(time)) {
                    return 1;
                }
                return 0;
            }
            return 0;
        }
        log.info("保存快照状态已完成或从未保存过，判断是否未保存过快照");
        if (this.databaseRepository.countFiles(this.cicdProjectId, dataBaseFileName) != 0) {
            return 1;
        }
        return 2;
    }

    /** 厂商 `getSnapShotVersion:1476-1486`。 */
    @Override
    public String getSnapShotVersion() {
        String dataBaseFileName = "dataBase_backup.zip";
        Long updatedAt = this.databaseRepository.queryFileUpdatedAt(this.cicdProjectId, dataBaseFileName);
        return updatedAt == null ? null : updatedAt.toString();
    }

    /** 厂商 `syncDatabaseInfo:1472-1474`：直接返回静态单例。 */
    @Override
    public DatabaseSyncStatusEntity syncDatabaseInfo() {
        return importStatus;
    }

    /** 厂商 `createDatabaseBackupProcess:770-781`。 */
    @Override
    public Process createDatabaseBackupProcess(String databaseName) {
        return this.databaseProcessPort.createBackupProcess(databaseName);
    }

    /** 厂商 `genGaopBackup:827-829`。 */
    @Override
    public File genGaopBackup() {
        return this.databaseProcessPort.dumpGaopToFile();
    }

    // ================================================================ 写

    /** 厂商 `addDatabaseInfo:566-590`。 */
    @Override
    public void addDatabaseInfo(String databaseName, String service, String desc, String userName,
                               InputStream fileStream, String originalFilename) throws IOException {
        String filName = cleanSqlFileName(originalFilename);
        log.info("当前sql文件名:" + filName);
        long timeMillis = System.currentTimeMillis();
        this.databaseRepository.createDatabase("`" + databaseName + "`");
        if (this.databaseRepository.queryDatabaseRows(databaseName).isEmpty()) {
            this.databaseRepository.insertDatabaseInfo(databaseName, service, desc);
        }
        long start = System.currentTimeMillis();
        log.info("开始执行导入sql");
        importSql(fileStream, filName, databaseName, timeMillis, userName, this.fileBackUp);
        log.info("结束导入sql，执行sql花费:{}毫秒", System.currentTimeMillis() - start);
        DatabaseEntity database = this.databaseRepository.queryDatabaseByName(databaseName).orElse(null);
        if (database != null) {
            database.setSqlName(timeMillis + "_" + filName);
            this.databaseRepository.updateDatabaseByPrimaryKey(database);
        }
    }

    /** 厂商在 3 处重复的同一段正则清洗（`:568-570`、`:620-622`、`DataBaseController:125-127`）。 */
    private static String cleanSqlFileName(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.replaceAll("\\s*|\t|\r|\n", "");
        String rex = "\\s*\\(.+\\)\\s*";
        return name.replaceAll(rex, "").replaceAll("[()]", "");
    }

    /** 厂商 `updateDatabaseByName:592-602`。 */
    @Override
    public DatabaseEntity updateDatabaseByName(String databaseName, DatabaseDescribeVO describe) {
        DatabaseEntity databaseByName = this.databaseRepository.queryDatabaseByName(databaseName).orElse(null);
        if (databaseByName == null) {
            this.databaseRepository.insertDatabaseInfo(databaseName, describe.getService(), describe.getDesc());
        } else {
            this.databaseRepository.updateDatabaseDescription(databaseName, describe.getService(), describe.getDesc());
            this.databaseRepository.updateDatabaseLastUser(databaseName, describe.getUserName());
        }
        // 厂商取 `list.get(0)`：这里若库里仍没有该行会抛 IndexOutOfBounds（照抄语义）
        return this.databaseRepository.queryDatabaseRows(databaseName).get(0);
    }

    /** 厂商 `updateDatabaseInitSqlFile:619-653`。 */
    @Override
    public boolean updateDatabaseInitSqlFile(String databaseName, String userName,
                                            InputStream fileStream, String originalFilename) throws IOException {
        String sqlFileName = cleanSqlFileName(originalFilename);
        log.info("当前sql文件名:" + sqlFileName);
        long timeMillis = System.currentTimeMillis();
        String originalName = timeMillis + "_" + sqlFileName;
        DatabaseEntity database = this.databaseRepository.queryDatabaseByName(databaseName).orElse(null);
        if (database == null) {
            log.info("更新初始化sql时未找到该数据库，开始进行插入");
            this.databaseRepository.insertDatabaseSelective(DatabaseEntity.builder()
                    .databaseName(databaseName).username(userName).sqlName(originalName).build());
        } else {
            String sqlName = database.getSqlName();
            if (sqlName != null) {
                File sqlFile = new File(this.fileBackUp, sqlName);
                if (sqlFile.exists()) {
                    log.info("存在旧初始化sql文件:{},进行移除", sqlName);
                    if (!sqlFile.delete()) {
                        log.warn("旧初始化sql文件删除失败:{}", sqlName);
                    }
                }
            }
            database.setSqlName(originalName);
            database.setUsername(userName);
            this.databaseRepository.updateDatabaseByPrimaryKey(database);
        }
        log.info("开始执行保存初始化sql到服务器上");
        saveFileByBack(fileStream, originalName);
        return true;
    }

    /** 厂商 `saveFileByBack:952-964`。 */
    private File saveFileByBack(InputStream in, String fileName) throws IOException {
        File filePath = new File(this.fileBackUp);
        if (!filePath.exists() && !filePath.mkdirs()) {
            saveDatabaseStatus = 3;
            saveDatabaseStatus = 0;
            throw new SystemException("global.save.file.failed");
        }
        File files = new File(filePath, fileName);
        Files.copy(in, files.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return files;
    }

    /** 厂商 `deleteDatabaseAllByName:713-731`（**不整体回滚**）。 */
    @Override
    public void deleteDatabaseAllByName(List<String> databaseNames) {
        List<String> databases = this.databaseRepository.showDatabases();
        for (String databaseName : databaseNames) {
            if (!databases.stream().anyMatch(s -> s.equals(databaseName))) {
                throw new WrongArgumentException("database.not.found.cannot.delete");
            }
            DatabaseEntity database = this.databaseRepository.queryDatabaseByName(databaseName).orElse(null);
            if (database != null && database.getSqlName() != null && !database.getSqlName().isEmpty()) {
                File file = new File(this.fileBackUp, database.getSqlName());
                if (file.exists()) {
                    log.info("存在数据库:{}的初始化sql文件:{}，进行移除", databaseName, database.getSqlName());
                    if (!file.delete()) {
                        log.warn("初始化sql文件删除失败:{}", database.getSqlName());
                    }
                }
            }
            this.databaseRepository.dropDatabase(databaseName);
            this.databaseRepository.deleteDatabaseRow(databaseName);
        }
    }

    /** 厂商 `deleteDatabaseByReset:733-761`。 */
    @Override
    public void deleteDatabaseByReset() {
        List<String> databases = this.databaseRepository.showDatabases();
        if (databases == null || databases.isEmpty()) {
            return;
        }
        for (String databaseName : databases) {
            if ("mysql".equals(databaseName) || "sys".equals(databaseName) || "information_schema".equals(databaseName)
                    || "performance_schema".equals(databaseName) || "gaop".equals(databaseName)) {
                log.info("获取到系统表:{}，跳过该表清除", databaseName);
                continue;
            }
            try {
                this.databaseRepository.dropDatabase(databaseName);
                DatabaseEntity database = this.databaseRepository.queryDatabaseByName(databaseName).orElse(null);
                if (database != null && database.getSqlName() != null && !database.getSqlName().isBlank()) {
                    File fileInfo = new File(this.fileBackUp, database.getSqlName());
                    if (fileInfo.exists()) {
                        log.info("开始移除初始化sql文件:{}", database.getSqlName());
                        if (!fileInfo.delete()) {
                            log.warn("初始化sql文件删除失败:{}", database.getSqlName());
                        }
                    }
                }
                this.databaseRepository.deleteDatabaseRow(databaseName);
            } catch (Exception e) {
                log.error("删除数据库" + databaseName + "出现异常:{}", e);
            }
        }
    }

    /**
     * 厂商 `saveDataBaseSnapShot:966-1209`。
     *
     * <p>照抄点：`type == 2 && BackUpJob.flag` 的**四处**中断检查、以及 if/else 两大段近乎重复的流程。
     * <p>缺的一段：`databaseUser.json` 条目（需要 `g_database_user` 表，见类 javadoc）。
     */
    @Override
    public boolean saveDataBaseSnapShot(File zipFile, String path, Integer type) throws IOException {
        log.info("开始保存数据库快照");
        long timeMillis = System.currentTimeMillis();
        saveDatabaseStatus = 1;
        String dataBaseFileName = "dataBase_backup.zip";
        String dbFileName = this.fileBackUp + File.separator + dataBaseFileName;
        String pathFileName = path + File.separator + dataBaseFileName;
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile));
        boolean exists = this.databaseRepository.countFiles(this.cicdProjectId, dataBaseFileName) != 0;
        if (exists) {
            File dbPath = new File(pathFileName);
            if (dbPath.exists() && !dbPath.delete()) {
                log.warn("旧的数据库快照包删除失败:{}", pathFileName);
            }
            if (type == 2 && interrupted(type)) {
                return false;
            }
            try {
                zipDataBaseFile(zipOut, type);
            } catch (Exception e) {
                saveDatabaseStatus = 3;
                saveDatabaseStatus = 0;
                log.error("压缩到zip出现异常,{}", e);
                throw new WrongArgumentException("backup.database.backup.compress.exception");
            }
            if (type == 2 && interrupted(type)) {
                return false;
            }
            // 缺的一段：databaseUser.json（g_database_user 未建模）
            if (type == 2 && interrupted(type)) {
                return false;
            }
            // 缺的一段：databaseUserRelation.json 里用的是 Jackson；这里手写 JSON（domain 禁 Jackson）
            List<DatabaseUserRelationInfoEntity> relationDataBaseList = getRelationDataBaseList();
            if (!relationDataBaseList.isEmpty()) {
                writeZipEntry(zipOut, "databaseUserRelation.json",
                        manualJsonRelations(relationDataBaseList).getBytes(StandardCharsets.UTF_8));
            }
            if (type == 2 && interrupted(type)) {
                return false;
            }
            Map<String, String> sqlDbInfo = collectSqlDbInfo();
            if (!sqlDbInfo.isEmpty()) {
                writeZipEntry(zipOut, "databaseSql.json",
                        manualJsonMap(sqlDbInfo).getBytes(StandardCharsets.UTF_8));
            }
            zipOut.flush();
            zipOut.close();
            if (type == 2 && interrupted(type)) {
                return false;
            }
            log.info("zip的大小:" + zipFile.length());
            if (countZipEntries(zipFile) != 0) {
                File dest = new File(path, dataBaseFileName);
                Files.copy(zipFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                this.databaseRepository.updateFile(this.cicdProjectId, dataBaseFileName, dbFileName,
                        zipFile.length(), timeMillis);
            } else {
                log.info("没有拿到任何初始化sql文件，不进行压缩生成数据库包");
            }
            saveDatabaseStatus = 2;
            saveDatabaseStatus = 0;
        } else {
            try {
                zipDataBaseFile(zipOut, type);
            } catch (Exception e) {
                saveDatabaseStatus = 3;
                saveDatabaseStatus = 0;
                log.error("压缩到zip出现异常,{}", e);
                throw new WrongArgumentException("backup.database.backup.compress.exception");
            }
            if (type == 2 && interrupted(type)) {
                return false;
            }
            // 缺的一段：databaseUser.json
            if (type == 2 && interrupted(type)) {
                return false;
            }
            List<DatabaseUserRelationInfoEntity> relationDataBaseList = getRelationDataBaseList();
            if (!relationDataBaseList.isEmpty()) {
                writeZipEntry(zipOut, "databaseUserRelation.json",
                        manualJsonRelations(relationDataBaseList).getBytes(StandardCharsets.UTF_8));
            }
            if (type == 2 && interrupted(type)) {
                return false;
            }
            Map<String, String> sqlDbInfo = collectSqlDbInfo();
            if (!sqlDbInfo.isEmpty()) {
                writeZipEntry(zipOut, "databaseSql.json", manualJsonMap(sqlDbInfo).getBytes(StandardCharsets.UTF_8));
            }
            zipOut.flush();
            zipOut.close();
            log.info("zip的大小:" + zipFile.length());
            if (type == 2 && interrupted(type)) {
                return false;
            }
            if (countZipEntries(zipFile) != 0) {
                File dest = new File(path, dataBaseFileName);
                Files.copy(zipFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                this.databaseRepository.insertFile(UUID.randomUUID().toString(), dataBaseFileName,
                        this.cicdProjectId, dbFileName, timeMillis, timeMillis, false, zipFile.length());
            } else {
                log.info("没有拿到任何初始化sql文件，不进行压缩生成数据库包");
            }
            saveDatabaseStatus = 2;
            saveDatabaseStatus = 0;
        }
        return true;
    }

    /**
     * 厂商 `type == 2 && BackUpJob.flag.booleanValue()` 的中断检查——照抄成方法以避免 12 处重复。
     * <p>厂商中断时会把 `backupStatus.setStatus(3)` 并推 STOMP `/topic/backup`，然后 `BackStatus.init()`。
     * 本切片未接 STOMP（见 javadoc）。
     */
    private boolean interrupted(Integer type) {
        if (type == 2 && com.csm.paas.core.domain.backup.service.BackupStatusHolder.jobInterrupted) {
            log.info("备份中断，已推送备份中断信息");
            com.csm.paas.core.domain.backup.service.BackupStatusHolder.backupStatus =
                    com.csm.paas.core.domain.backup.model.entity.BackupStatusEntity.init();
            return true;
        }
        return false;
    }

    /** 厂商 `collectSqlDbInfo` 等价段（`:1052-1066` 与 `:1157-1171`）。 */
    private Map<String, String> collectSqlDbInfo() {
        Map<String, String> sqlDbInfo = new LinkedHashMap<>();
        List<DatabaseEntity> databaseList = this.databaseRepository.queryAllDatabaseRows();
        if (databaseList != null && !databaseList.isEmpty()) {
            databaseList.forEach(d -> {
                if (d.getSqlName() != null && !d.getSqlName().isBlank()) {
                    sqlDbInfo.put(d.getSqlName(), d.getDatabaseName());
                }
            });
        }
        return sqlDbInfo;
    }

    /** 手写 JSON：`[{"databaseName":..,"databaseUserList":[..]}]`（domain 禁用 Jackson 的替代）。 */
    private static String manualJsonRelations(List<DatabaseUserRelationInfoEntity> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            DatabaseUserRelationInfoEntity e = list.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"databaseName\":").append(jsonString(e.getDatabaseName())).append(",\"databaseUserList\":[");
            List<String> users = e.getDatabaseUserList();
            if (users != null) {
                for (int j = 0; j < users.size(); j++) {
                    if (j > 0) {
                        sb.append(',');
                    }
                    sb.append(jsonString(users.get(j)));
                }
            }
            sb.append("]}");
        }
        return sb.append(']').toString();
    }

    /** 手写 JSON：`{"<sqlName>":"<dbName>"}`。 */
    private static String manualJsonMap(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(jsonString(e.getKey())).append(':').append(jsonString(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String jsonString(String raw) {
        if (raw == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** 厂商 `fileAllToZip:880-887`（**不关输入流**）。 */
    private void writeZipEntry(ZipOutputStream zipOut, String fileName, byte[] content) throws IOException {
        zipOut.putNextEntry(new ZipEntry(fileName));
        zipOut.write(content);
        zipOut.closeEntry();
    }

    /** 厂商 `countZipEntries` 等价：`new ZipFile(zipFile).size() != 0`。 */
    private static int countZipEntries(File zipFile) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            return zip.size();
        }
    }

    /** 厂商 `zipDataBaseFile:1222-1264`。 */
    private void zipDataBaseFile(ZipOutputStream zipOut, Integer type) throws IOException {
        String errorMsg = "当前没有获取到数据库列表，无法进行保存数据库快照";
        List<File> list = getFileListByBackUpByInitSqlFile(errorMsg);
        if (type == 2 && interrupted(type)) {
            return;
        }
        if (list != null) {
            for (File f : list) {
                if (type == 2 && interrupted(type)) {
                    return;
                }
                String name = f.getName();
                try (InputStream in = Files.newInputStream(f.toPath())) {
                    zipOut.putNextEntry(new ZipEntry(name));
                    byte[] bufferArea = new byte[10240];
                    int length;
                    while ((length = in.read(bufferArea, 0, 10240)) != -1) {
                        zipOut.write(bufferArea, 0, length);
                    }
                    log.info("当前压缩内部的文件名称:" + name);
                } catch (java.io.FileNotFoundException e) {
                    log.error("压缩文件时未找到名称为:" + name + "的文件，异常为:", e);
                    zipOut.close();
                    throw new WrongArgumentException("compress.file.not.found", name);
                } catch (IOException e) {
                    log.error("压缩文件时出现异常，异常为:", e);
                    zipOut.close();
                    throw new WrongArgumentException("compress.file.exception");
                }
            }
        }
    }

    /** 厂商 `getFileListByBackUpByInitSqlFile:2224-2253`。 */
    public List<File> getFileListByBackUpByInitSqlFile(String errorMsg) {
        List<File> fileList = new ArrayList<>();
        List<BusinessDatabaseNameEntity> databaseItems = this.databaseRepository.allDatabasesTableCount(null);
        List<String> dbNames = databaseItems.stream().map(BusinessDatabaseNameEntity::getName).toList();
        if (dbNames.isEmpty()) {
            log.info(errorMsg);
            return null;
        }
        List<DatabaseEntity> databaseList = this.databaseRepository.queryAllDatabaseRows();
        if (databaseList == null || databaseList.isEmpty()) {
            log.info("未找到数据库存储的数据信息，不进行数据库保存");
            return null;
        }
        for (DatabaseEntity data : databaseList) {
            if (data.getSqlName() != null && !data.getSqlName().isBlank()) {
                File file = new File(this.fileBackUp, data.getSqlName());
                if (!file.exists()) {
                    log.info("在服务器上没有找到名称为:{}的文件，不进行保存备份", data.getSqlName());
                    continue;
                }
                fileList.add(file);
                continue;
            }
            log.info("从库中取到数据库:{}的sql文件名字段为空，不进行该数据库备份", data.getDatabaseName());
        }
        return fileList;
    }

    /** 厂商 `importSql:235-268`。 */
    @Override
    public DatabaseExecResultEntity importSql(InputStream fileStream, String fileName, String databaseName,
                                              long timeMillis, String userName, String saveFilePath) throws IOException {
        DatabaseEntity optionalDatabase = DatabaseEntity.builder()
                .username(userName).databaseName(databaseName).build();
        createDatabaseIfNotExist(databaseName, optionalDatabase);
        log.info("保存到指定位置...");
        String originalFileName = timeMillis + "_" + fileName;
        saveFile(fileStream, originalFileName, saveFilePath);
        File sqlFile = new File(saveFilePath + File.separator + originalFileName);
        if (!sqlFile.exists()) {
            throw new IOException("文件保存失败，路径不存在: " + sqlFile.getAbsolutePath());
        }
        long totalBytes = sqlFile.length();
        log.info("SQL文件已就绪: {}, 大小: {} bytes", sqlFile.getAbsolutePath(), totalBytes);
        if (!this.databaseProcessPort.checkConnect(databaseName)) {
            log.error("连接数据库超时");
            throw new WrongArgumentException("database.connect.timeout");
        }
        this.databaseProcessPort.executeSqlFile(databaseName, sqlFile);
        try {
            addDatabaseRecord(fileName, timeMillis, databaseName, 1, null);
            return DatabaseExecResultEntity.builder().success(true).build();
        } catch (Exception e) {
            log.error("保存数据库记录异常: {}", e.getMessage());
            return DatabaseExecResultEntity.builder().success(false)
                    .error("Save Record Failed").cause(e.getMessage()).build();
        }
    }

    /** 厂商 `createDatabaseIfNotExist:270-296`。 */
    private void createDatabaseIfNotExist(String databaseName, DatabaseEntity optionalData) {
        if (!existDatabase(databaseName)) {
            this.databaseRepository.createDatabase("`" + databaseName + "`");
            log.info("创建数据库:{} 成功", databaseName);
        }
        DatabaseEntity databaseByName = this.databaseRepository.queryDatabaseByName(databaseName).orElse(null);
        if (databaseByName == null) {
            log.info("数据库中未找到:{} 数据库，进行插入该数据库信息", databaseName);
            DatabaseEntity database = DatabaseEntity.builder().databaseName(databaseName).build();
            if (optionalData != null) {
                database.setUsername(optionalData.getUsername());
                database.setService(optionalData.getService());
                database.setSqlName(optionalData.getSqlName());
            }
            this.databaseRepository.insertDatabaseSelective(database);
        } else {
            if (optionalData != null) {
                databaseByName.setUsername(optionalData.getUsername());
                databaseByName.setService(optionalData.getService());
                databaseByName.setSqlName(optionalData.getSqlName());
            }
            this.databaseRepository.updateDatabaseByPrimaryKeySelective(databaseByName);
        }
    }

    /** 厂商 `addDatabaseRecord:1266-1277`。 */
    public void addDatabaseRecord(String originalFileName, long now, String dbName, Integer status, String faultMsg) {
        this.databaseRepository.insertRecord(DatabaseRecordEntity.builder()
                .id(UUID.randomUUID().toString())
                .realStoreFilename(now + originalFileName)
                .execFileName(originalFileName)
                .databaseName(dbName == null ? null : dbName.toLowerCase())
                .updatedAt(now)
                .status(status)
                .execFaultMessage(faultMsg)
                .build());
    }

    /** 厂商 `saveFile:1284-1294`。 */
    private File saveFile(InputStream in, String sqlFileName, String path) throws IOException {
        File filePath = new File(path);
        if (!filePath.exists() && !filePath.mkdirs()) {
            throw new SystemException("global.save.file.failed");
        }
        File file = new File(filePath, sqlFileName);
        Files.copy(in, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("复制输入流 ：{}文件", file.getAbsolutePath());
        return file;
    }

    /**
     * 厂商 `importDataBase:1741-2038`（`@Async`）。
     *
     * <p>照抄点：逐个 zip 条目导入；`^\d+_.*` 的**初始化 sql 命名**会被去掉时间前缀再入库；
     * `databaseSql.json` 里的映射会把"时间戳前缀名"纠正回**真实库名**；
     * 结尾 `newSqlName` 与 `timeSqlName` 两个分支写的内容**完全一样**（照抄）。
     *
     * <p>缺的一段：结尾的 `tenxCloud.clusters()` + `restartService`（K8s 侧重启全部业务服务）
     * 与全部 STOMP 推送。
     */
    @Override
    public DatabaseImportSqlInfoEntity importDataBase(InputStream in, String fileName, Integer size,
                                                      DatabaseImportInfoEntity importInfo) throws IOException {
        importStatus.setSyncing(true);
        importStatus.setSuccess(false);
        importStatus.setCurrent(0);
        List<String> successList = new ArrayList<>();
        List<String> errorList = new ArrayList<>();
        long start = System.currentTimeMillis();
        log.info("获取到压缩包中一共{}个文件", size);
        importStatus.setTotal(size);
        Map<String, String> sqlHashMap = importInfo == null ? null : importInfo.getSqlHashMap();
        List<DatabaseUserRelationInfoEntity> relationList =
                importInfo == null ? List.of() : importInfo.getDatabaseUserRelationInfoList();
        try (ZipInputStream zipInputStream = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry ze;
            while ((ze = zipInputStream.getNextEntry()) != null) {
                String sqlName = ze.getName();
                if (sqlName.endsWith(".json")) {
                    log.info("跳过json文件:{}", sqlName);
                    continue;
                }
                sqlName = sqlName.replaceAll("\\s*|\t|\r|\n", "");
                String rex = "\\s*\\(.+\\)\\s*";
                sqlName = sqlName.replaceAll(rex, "").replaceAll("[()]", "");
                log.info("当前sql文件名:" + sqlName);
                String dbName = sqlName.substring(0, sqlName.length() - 4);
                if (dbName.matches("^\\d+_.*")) {
                    dbName = dbName.replaceAll("^\\d+_", "");
                }
                if (sqlHashMap != null && sqlHashMap.containsKey(sqlName)) {
                    dbName = sqlHashMap.get(sqlName);
                }
                int i = importStatus.getCurrent() + 1;
                importStatus.setCurrent(i);
                log.info("当前导入的数据库是第{}个，名称为:{}", i, dbName);
                com.csm.paas.core.domain.backup.service.BackupLogHolder.sendRecoveryLog("database",
                        "当前导入的数据库是第" + i + "个，名称为:" + dbName, null, "info");
                importStatus.setCurrentDatabaseName(dbName);
                if (!existDatabase(dbName)) {
                    this.databaseRepository.createDatabase("`" + dbName + "`");
                    if (this.databaseRepository.queryDatabaseByName(dbName).isEmpty()) {
                        this.databaseRepository.insertDatabaseSelective(
                                DatabaseEntity.builder().databaseName(dbName).username("admin").build());
                    }
                }
                File tmp = File.createTempFile("tmp", ".sql");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buff = new byte[1024];
                    int l;
                    while ((l = zipInputStream.read(buff)) != -1) {
                        out.write(buff, 0, l);
                    }
                    out.flush();
                } catch (IOException e) {
                    importStatus.setSuccess(false);
                    importStatus.setSyncing(false);
                    importStatus.setCurrent(0);
                    throw new WrongArgumentException("database.parse.sql.file.exception", sqlName);
                }
                long timeMillis = System.currentTimeMillis();
                String originalFileName = timeMillis + "_" + sqlName;
                try (InputStream fileInputStream = Files.newInputStream(tmp.toPath())) {
                    saveFile(fileInputStream, originalFileName, this.fileBackUp);
                }
                String timeSqlName = timeMillis + "_" + (sqlName.matches("^\\d+_.*")
                        ? sqlName.replaceAll("^\\d+_", "") : sqlName);
                this.databaseRepository.queryDatabaseByName(dbName).ifPresent(database -> {
                    database.setSqlName(timeSqlName);
                    this.databaseRepository.updateDatabaseByPrimaryKeySelective(database);
                });
                String filePath = new File(this.fileBackUp, originalFileName).getPath();
                if (!this.databaseProcessPort.checkConnect(dbName)) {
                    throw new WrongArgumentException("database.connect.timeout");
                }
                try {
                    DatabaseExecResultEntity result = this.databaseProcessPort.executeSqlFile(dbName, new File(filePath));
                    if (result.isSuccess()) {
                        addDatabaseRecord(sqlName, timeMillis, dbName, 1, null);
                        successList.add(dbName);
                    } else {
                        addDatabaseRecord(sqlName, timeMillis, dbName, 0, result.getLog());
                        errorList.add(dbName);
                    }
                } catch (Exception e) {
                    importStatus.setSuccess(false);
                    importStatus.setSyncing(false);
                    importStatus.setCurrent(0);
                    log.error("导入SQL文件[{}]到数据库[{}]时发生异常:", sqlName, dbName, e);
                    throw new WrongArgumentException("database.import.sql.file.error", sqlName);
                } finally {
                    Files.deleteIfExists(tmp.toPath());
                }
                if (importStatus.getCurrent() == importStatus.getTotal()) {
                    log.info("导入完成，推送所有导入结果给前端");
                    importStatus.setSuccess(true);
                    importStatus.setSyncing(false);
                    importStatus.setCurrent(importStatus.getTotal());
                    importStatus.setSuccess(false);
                    importStatus.setSyncing(false);
                    importStatus.setCurrent(0);
                    if (!relationList.isEmpty()) {
                        importDatabaseUserRelationInfo(relationList);
                    } else {
                        log.info("没有数据库用户关联数据库信息，不进行关联操作");
                    }
                }
            }
        } finally {
            importStatus.setSyncing(false);
        }
        long time = System.currentTimeMillis() - start;
        log.info("调用接口花费时间为:{}毫秒", time);
        return DatabaseImportSqlInfoEntity.builder().successList(successList).errorList(errorList).build();
    }

    /** 厂商 `importDatabaseUserRelationInfo:2398-2422`。 */
    public boolean importDatabaseUserRelationInfo(List<DatabaseUserRelationInfoEntity> list) {
        for (DatabaseUserRelationInfoEntity userRelationInfo : list) {
            List<BusinessDatabaseNameEntity> databaseItems =
                    this.databaseRepository.allDatabasesTableCount(userRelationInfo.getDatabaseName());
            if (databaseItems.isEmpty()) {
                log.info("进行关联时查找数据库:{}未找到，跳过该数据库的关联信息导入",
                        userRelationInfo.getDatabaseName());
                continue;
            }
            for (String databaseUser : userRelationInfo.getDatabaseUserList()) {
                String user = this.databaseRepository.queryAccessUser(databaseUser);
                if (user == null || user.isBlank()) {
                    log.info("未找到数据库用户:{}不进行关联", databaseUser);
                    continue;
                }
                List<String> belongUsers = this.databaseRepository.databaseBelongUsers(userRelationInfo.getDatabaseName());
                if (!belongUsers.isEmpty() && belongUsers.contains(databaseUser)) {
                    log.info("查询数据库:{}已包含数据库用户:{}的访问权限，跳过该用户的关联操作",
                            userRelationInfo.getDatabaseName(), databaseUser);
                    continue;
                }
                this.databaseRepository.databasesAccessControl(userRelationInfo.getDatabaseName(), databaseUser);
                this.databaseRepository.refreshPermission();
            }
        }
        return true;
    }

    /** 厂商 `importSqlFromApplication:209-233`。 */
    @Override
    public DatabaseExecResultEntity importSqlFromApplication(File sqlFile, String databaseName,
                                                             String service, String username) throws IOException {
        long prefix = System.currentTimeMillis();
        String originalName = prefix + "_" + sqlFile.getName();
        try (InputStream in = Files.newInputStream(sqlFile.toPath())) {
            saveFile(in, originalName, this.fileBackUp);
        }
        DatabaseEntity optionalData = DatabaseEntity.builder()
                .sqlName(originalName).service(service).username(username).build();
        createDatabaseIfNotExist(databaseName, optionalData);
        DatabaseExecResultEntity sqlResult = this.databaseProcessPort.executeSqlFile(databaseName, sqlFile);
        this.databaseRepository.insertRecord(DatabaseRecordEntity.builder()
                .id(UUID.randomUUID().toString())
                .realStoreFilename(originalName)
                .execFileName(sqlFile.getName())
                .databaseName(databaseName.toLowerCase())
                .updatedAt(prefix)
                .status(sqlResult.isSuccess() ? 1 : 0)
                .execFaultMessage(sqlResult.getError() == null ? "" : sqlResult.getError())
                .build());
        return sqlResult;
    }

    /** 厂商 `executeSQLFromImport:510-552`（controller 未直接暴露，保留给软件启动流程）。 */
    public DatabaseExecResultEntity executeSQLFromImport(InputStream file, String databaseName) {
        return this.databaseProcessPort.executeSqlScript(databaseName, file);
    }

    /** 厂商 `creatDatabase:554-556`。 */
    public void creatDatabase(String databaseName) {
        this.databaseRepository.createDatabase(databaseName);
    }

    /**
     * 厂商 `backupDatabaseDataAndSyncOtherNode:2467-2487`——**厂商字节码本身反编译失败**
     * （CFR 只留下一行 `throw new IllegalStateException("Decompilation failed")`，见
     * `recovered/gaop-be/.../service/DataBaseService.java:2464-2487`）。
     *
     * <p>因此本方法**无法照抄**。控制器保留入口与厂商的入参校验（空列表 → 400
     * `multiple.master.buckup.table.not.table.parameter`），服务层如实抛
     * {@link UnsupportedOperationException}（HTTP 500 / code -1 信封）。
     */
    public boolean backupDatabaseDataAndSyncOtherNode() {
        throw new UnsupportedOperationException(
                "厂商 DataBaseService.backupDatabaseDataAndSyncOtherNode 反编译失败，无法照抄实现");
    }

    /** 厂商 `getFileListByBackUpSetting` 的 type=0 分支用到的 dump（保留给快照的自定义设置路径）。 */
    public File dumpDatabaseForSetting(String databaseName, Integer type) {
        return this.databaseProcessPort.dumpToFile(databaseName, type != null && type == 0);
    }

    /** 供 `databaseProcessPort` 之外的调用方判断进程超时（厂商 `waitFor(1800, SECONDS)`）。 */
    static boolean waitFor(Process process, long timeout, TimeUnit unit) throws InterruptedException {
        return process.waitFor(timeout, unit);
    }
}
