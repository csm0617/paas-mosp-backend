package com.csm.paas.core.domain.database.adapter.port;

import com.csm.paas.core.domain.database.model.entity.DatabaseExecResultEntity;

import java.io.File;
import java.io.InputStream;

/**
 * MySQL 命令行进程端口（mysqldump / mysql + ProcessBuilder）。
 *
 * <p><b>类 C：本环境不可验证</b>——本环境没有可执行的 `mysqldump`/`mysql` 客户端，
 * 也没有第二数据源主机；代码按厂商逐行照抄，无法与厂商对拉。
 *
 * <p>厂商出处（全部在 {@code service/DataBaseService.java}，直接 `new ProcessBuilder(...)`）：
 * <ul>
 *   <li>`createDatabaseBackupProcess:770-781`：`mysqldump --protocol=TCP -h<host> --extended-insert
 *       --port=<port> -u<user> -p<pwd> <db>` → 进程 stdout 直接回写响应（`/v1/database/export/{db}`）</li>
 *   <li>`genDataBaseBackup:783-821`：`mysqldump ... <db> -r <db>.sql`（**写到进程工作目录的相对路径**），
 *       超时 1800s；文件不存在 → `SystemException("database.backup.file.generate.failed")`</li>
 *   <li>`checkDatabaseConnect:475-503`：`mysql --protocol=TCP -h.. --port=.. -u.. -p.. -e "USE <db>"`，
 *       10s 内退出即视为连通（**不看退出码，只看是否 waitFor 成功** —— 照抄）</li>
 *   <li>`executeSqlToDatabase:301-423`：把 sql 文件流式喂给 `mysql ... <db>` 的 stdin，
 *       超时 10 分钟；进度回调只在间隔 &gt;1s 时触发</li>
 *   <li>`executeSQLFromImport:510-552`：`ScriptRunner` + `UnicodeReader(file,"utf-8")`，
 *       `stopOnError(false)`，**最后仍返回 success=true**（厂商把 builder 的赋值丢了，见方法注释）</li>
 *   <li>`getFileListByBackUpSetting:2255-2376`：按 `g_backup_setting.type` 决定是否加 `--no-data`</li>
 * </ul>
 *
 * <p>连接参数来自厂商 `spring.datasource.*`（url/username/password）——domain 拿不到 DataSource，
 * 所以这些参数在 infrastructure 的实现里用 `@Value` 读。
 */
public interface IDatabaseProcessPort {

    /** 厂商 `createDatabaseBackupProcess`：进程 stdout 就是 mysqldump 输出，调用方直接 copy。 */
    Process createBackupProcess(String databaseName);

    /**
     * 厂商 `genDataBaseBackup`：`mysqldump ... <db> -r <db>.sql`，返回**相对路径**的 `File`。
     *
     * @param noData true 时加 `--no-data`（厂商 `getFileListByBackUpSetting` 的 type=0 分支）
     */
    File dumpToFile(String databaseName, boolean noData);

    /** 厂商 `genGaopBackup`：对 **gaop** 数据源（`@Qualifier("gaop-mysql")`）做 `dumpToFile("gaop", false)`。 */
    File dumpGaopToFile();

    /** 厂商 `checkDatabaseConnect`：10s 内进程结束即 true（无论退出码）。 */
    boolean checkConnect(String databaseName);

    /** 厂商 `executeSqlToDatabase`：`mysql ... <db>` 吃 stdin，返回**始终 success=true** 的最终结果。 */
    DatabaseExecResultEntity executeSqlFile(String databaseName, File sqlFile);

    /** 厂商 `executeSQLFromImport`：`ScriptRunner` 在一个连接里执行整个流（`stopOnError(false)`）。 */
    DatabaseExecResultEntity executeSqlScript(String databaseName, InputStream sql);
}
