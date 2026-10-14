package com.csm.paas.core.infrastructure.gateway;

import com.csm.paas.core.domain.database.adapter.port.IDatabaseProcessPort;
import com.csm.paas.core.domain.database.model.entity.DatabaseExecResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * MySQL 命令行进程的端口实现。
 *
 * <p><b>类 C：本环境不可验证</b>——本环境没有 `mysqldump` / `mysql` 客户端可执行文件，
 * 也没有厂商那样的第二数据源主机；代码逐行照抄厂商
 * {@code service/DataBaseService.java:301-423、475-503、510-552、770-821、2094-2148}，
 * 但**全程无法与厂商对拉**。
 *
 * <p>连接参数：厂商是从 `DruidDataSource` 里取 `getUrl()/getUsername()/getPassword()`
 * （`@Qualifier("mysql")` 的**第二数据源**）。本仓只有一个数据源（`spring.datasource.*`，
 * 指向 `gaop` 库的同一台 MySQL），所以这里 `@Value` 直读主数据源配置——
 * 若部署时管理连接真的在另一台主机上，需要补一个独立 DataSource。
 */
@Slf4j
@Component
public class DatabaseProcessGateway implements IDatabaseProcessPort {

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    /** 厂商 `mysql.dump.exec`（`application.yml:127-128`，默认 `mysqldump`）。 */
    @Value("${mysql.dump.exec:mysqldump}")
    private String mysqldumpExec;

    /** 厂商 `mysql.import.exec`（`application.yml:129-130`，默认 `mysql`）。 */
    @Value("${mysql.import.exec:mysql}")
    private String mysqlImportExec;

    public DatabaseProcessGateway(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 厂商 `DatabaseService.extractDatabaseUrl:763-768`：`url.substring(5)` 后交给 `URI.create`。 */
    private URI extractUri() {
        String url = this.datasourceUrl;
        if (url == null || url.length() < 5) {
            throw new IllegalStateException("spring.datasource.url is not configured");
        }
        return URI.create(url.substring(5));
    }

    /** 厂商 `createDatabaseBackupProcess:770-781`。 */
    @Override
    public Process createBackupProcess(String databaseName) {
        URI uri = extractUri();
        try {
            return new ProcessBuilder(this.mysqldumpExec, "--protocol=TCP", "-h" + uri.getHost(),
                    "--extended-insert", "--port=" + uri.getPort(),
                    "-u" + this.datasourceUsername, "-p" + this.datasourcePassword, databaseName).start();
        } catch (Exception e) {
            log.error("mysqldump执行失败", e);
            throw new com.csm.paas.core.types.exception.SystemException("database.backup.error.name");
        }
    }

    /**
     * 厂商 `genDataBaseBackup:783-821`（`mysqldump ... <db> -r <db>.sql`，相对路径）
     * ＋ `getFileListByBackUpSetting:2298-2335` 的 `--no-data` 分支（type=0）。
     */
    @Override
    public File dumpToFile(String databaseName, boolean noData) {
        URI uri = extractUri();
        String fileName = databaseName + ".sql";
        List<String> commands = new ArrayList<>();
        commands.add(this.mysqldumpExec);
        commands.add("--protocol=TCP");
        commands.add("-h" + uri.getHost());
        commands.add("--extended-insert");
        commands.add("--port=" + uri.getPort());
        commands.add("-u" + this.datasourceUsername);
        commands.add("-p" + this.datasourcePassword);
        if (noData) {
            commands.add("--no-data");
        }
        commands.add(databaseName);
        commands.add("-r");
        commands.add(fileName);
        Process process;
        try {
            process = new ProcessBuilder(commands).inheritIO().start();
        } catch (Exception e) {
            log.error("mysqldump执行失败", e);
            throw new com.csm.paas.core.types.exception.SystemException("database.backup.error.name");
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                log.info(line);
            }
            long timeout = 1800L;
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                log.info("等待导出文件超时，当前设置的时间为：{}秒", timeout);
                log.error("数据库{}备分子线程退出异常", databaseName);
            }
        } catch (IOException | InterruptedException e) {
            log.error("数据库备份文件生成失败", e);
            throw new com.csm.paas.core.types.exception.SystemException("database.backup.file.generate.failed");
        } catch (Exception e) {
            log.error("数据库备份执行失败", e);
            throw new com.csm.paas.core.types.exception.SystemException("database.backup.file.generate.failed");
        }
        File file = new File(fileName);
        if (!file.exists()) {
            throw new com.csm.paas.core.types.exception.SystemException("database.backup.file.generate.failed");
        }
        return file;
    }

    /** 厂商 `genGaopBackup:827-829`（`genDataBaseBackup(gaopMysql, "gaop")`）。 */
    @Override
    public File dumpGaopToFile() {
        return dumpToFile("gaop", false);
    }

    /** 厂商 `checkDatabaseConnect:475-503`：**只看进程是否在 10s 内结束，不看退出码**。 */
    @Override
    public boolean checkConnect(String databaseName) {
        URI uri = extractUri();
        ProcessBuilder processBuilder = new ProcessBuilder(this.mysqlImportExec, "--protocol=TCP",
                "-h" + uri.getHost(), "--port=" + uri.getPort(),
                "-u" + this.datasourceUsername, "-p" + this.datasourcePassword,
                "-e", "USE " + databaseName);
        try {
            Process process = processBuilder.start();
            try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String s;
                while ((s = stdInput.readLine()) != null) {
                    log.debug("尝试连接数据库，输出：" + s);
                }
                StringBuilder errorOutput = new StringBuilder();
                while ((s = stdError.readLine()) != null) {
                    errorOutput.append(s).append("\n");
                }
                boolean connected = process.waitFor(10L, TimeUnit.SECONDS);
                if (connected) {
                    log.debug("连接成功");
                    return true;
                }
                log.error("连接数据库失败：错误信息:{}", errorOutput);
                return false;
            }
        } catch (Exception e) {
            log.error("连接数据库异常", e);
            return false;
        }
    }

    /**
     * 厂商 `executeSqlToDatabase:301-423`。
     *
     * <p>**照抄点**：所有 `catch` 分支里 `result.xxx().build()` 的返回值都被丢弃，
     * 方法只在一个地方 `return ...success(true).build()` ⇒ 只要进程能起来，
     * **这个方法几乎总是报 success=true**。这里如实照抄（`log`/`exitCode` 会带上真实信息）。
     */
    @Override
    public DatabaseExecResultEntity executeSqlFile(String databaseName, File sqlFile) {
        long totalBytes = sqlFile.length();
        DatabaseExecResultEntity.DatabaseExecResultEntityBuilder result =
                DatabaseExecResultEntity.builder().success(false);
        URI uri = extractUri();
        String os = System.getProperty("os.name");
        List<String> commands = new ArrayList<>();
        commands.add(this.mysqlImportExec);
        commands.add("-h");
        commands.add(uri.getHost());
        commands.add("-P");
        commands.add(String.valueOf(uri.getPort()));
        commands.add("-u");
        commands.add(this.datasourceUsername);
        commands.add("-p" + this.datasourcePassword);
        commands.add("--default-character-set");
        commands.add("utf8mb4");
        if (os != null && os.toLowerCase(Locale.ROOT).startsWith("windows")) {
            commands.add("--ssl-mode");
            commands.add("DISABLED");
        }
        commands.add("--max-allowed-packet=64M");
        commands.add("--net-buffer-length=16384");
        commands.add("--compress");
        commands.add(databaseName);
        log.debug("执行命令导入sql：" + String.join(" ", commands));
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectErrorStream(true);
        Process process = null;
        StringBuilder logOutput = new StringBuilder();
        try {
            log.info("开始启动 MySQL 导入进程...");
            process = pb.start();
            Process finalProcess = process;
            Thread errorGobbler = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(finalProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logOutput.append("error: ").append(line).append("\n");
                    }
                } catch (Exception e) {
                    logOutput.append("error: ").append(e).append("\n");
                }
            });
            errorGobbler.start();
            try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(sqlFile));
                 OutputStream output = process.getOutputStream()) {
                int bytesRead;
                byte[] buffer = new byte[65536];
                long writtenBytes = 0L;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    writtenBytes += bytesRead;
                }
            }
            boolean finished = process.waitFor(10L, TimeUnit.MINUTES);
            int exitCode = finished ? process.exitValue() : -1;
            String errorLogContent = logOutput.toString();
            if (!finished || exitCode != 0) {
                process.destroyForcibly();
                log.error("MySQL 导入失败. ExitCode: {}, Log: {}", exitCode, errorLogContent);
                // 照抄厂商：这里 build() 的返回值被丢弃
                result.success(false).error(errorLogContent).exitCode(exitCode).build();
            }
        } catch (InterruptedException e) {
            log.error("执行被中断", e);
            if (process != null) {
                process.destroyForcibly();
            }
            result.success(false).cause(e.getMessage()).build();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("导入过程异常", e);
            result.success(false).cause(e.getMessage()).build();
        } finally {
            log.debug("导入过程错误日志：" + logOutput);
        }
        // 厂商 `:422`：无条件成功
        return result.exitCode(0).log(logOutput.toString()).success(true).build();
    }

    /**
     * 厂商 `executeSQLFromImport:510-552`（`ScriptRunner` + `UnicodeReader`）。
     *
     * <p>厂商用 `org.fife.io.UnicodeReader`；本仓没有该依赖（也没引 POI/RSTA），
     * 用 `InputStreamReader(UTF_8)` 等价替代（UnicodeReader 在这里也是按 utf-8 传的）。
     */
    @Override
    public DatabaseExecResultEntity executeSqlScript(String databaseName, InputStream sql) {
        try (Connection conn = this.dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var statement = conn.createStatement()) {
                statement.execute("use `" + databaseName + "`;");
            }
            try {
                ScriptRunner runner = new ScriptRunner(conn);
                BufferedReader reader = new BufferedReader(new InputStreamReader(sql, StandardCharsets.UTF_8));
                runner.setStopOnError(false);
                runner.runScript(reader);
                return DatabaseExecResultEntity.builder().success(true).build();
            } catch (Exception e) {
                log.error("执行sql失败", e);
                conn.rollback();
                return DatabaseExecResultEntity.builder().success(false).error(e.getMessage())
                        .cause(e.getCause() == null ? "" : e.getCause().getMessage()).build();
            }
        } catch (SQLException e) {
            log.error("sql导入执行异常", e);
            return DatabaseExecResultEntity.builder().success(false).error(e.getMessage())
                    .cause(e.getCause() == null ? "" : e.getCause().getMessage()).build();
        }
    }

    /** 厂商 `initCommands:2094-2148` 的 wrapper sql（`SET autocommit=0; source ...; COMMIT;`）。 */
    public static File buildWrapperSql(File sqlFile) throws IOException {
        File wrapperSql = File.createTempFile("import_wrapper", ".sql");
        try (PrintWriter pw = new PrintWriter(new FileWriter(wrapperSql, StandardCharsets.UTF_8))) {
            pw.println("SET autocommit=0;");
            pw.println("source " + sqlFile.getAbsolutePath().replace("\\", "/") + ";");
            pw.println("COMMIT;");
        }
        return wrapperSql;
    }
}
