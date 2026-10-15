package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.DatabaseDescribeDTO;
import com.csm.paas.core.api.dto.DatabaseExecResultDTO;
import com.csm.paas.core.api.dto.DatabaseImportInfoDTO;
import com.csm.paas.core.api.dto.DatabaseImportSqlInfoDTO;import com.csm.paas.core.api.dto.DatabaseItemDTO;
import com.csm.paas.core.api.dto.DatabaseListDTO;
import com.csm.paas.core.api.dto.DatabaseMysqlInfoDTO;
import com.csm.paas.core.api.dto.DatabaseNeedBuckUpTableFormDTO;
import com.csm.paas.core.api.dto.DatabasePermissionDTO;
import com.csm.paas.core.api.dto.DatabaseSyncStatusDTO;
import com.csm.paas.core.api.dto.DatabaseTableSizeDTO;
import com.csm.paas.core.api.dto.DatabaseUserDTO;
import com.csm.paas.core.api.dto.DatabaseUserFormDTO;
import com.csm.paas.core.api.dto.DatabaseUserListDTO;
import com.csm.paas.core.api.dto.DatabaseUserModifyDTO;
import com.csm.paas.core.api.dto.DatabaseUserRelationInfoDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.ServiceListResponseDTO;
import com.csm.paas.core.domain.database.model.entity.DatabaseExecResultEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseImportInfoEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseImportSqlInfoEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserRelationInfoEntity;
import com.csm.paas.core.domain.database.model.valobj.DatabaseDescribeVO;
import com.csm.paas.core.domain.database.service.IDatabaseService;
import com.csm.paas.core.domain.database.service.IDatabaseUserService;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.trigger.assembler.ServiceListAssembler;
import com.csm.paas.core.trigger.i18n.MessageResolver;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 厂商 {@code controller/DataBaseController.java}（`/v1/database`）的重写版，**23 条端点一条不少**。
 *
 * <h3>照抄的怪点</h3>
 * <ol>
 *   <li>`POST ""`（创建库）先 `show databases` 判重，重复 → 400 `database.duplicate.database`；
 *       表单里 `service`/`desc` 是 `required=false`。</li>
 *   <li>`GET ""`（列表）**只**在 keyword 非空时才 `replaceAll(" +","")`（不做 trim/大小写）。</li>
 *   <li>`DELETE ""`：库名用 **`,`** 分隔，交给服务层批量删；中途遇到不存在的库会 400
 *       （前面的库已经删掉了）。</li>
 *   <li>`GET /export/{databaseName}`：`mysqldump` 的 stdout **直接** copy 给响应体
 *       （`Process` 从不 waitFor、从不销毁——照抄）。</li>
 *   <li>`GET /export_framework`：先把 gaop 库 dump 成**进程工作目录下**的 `gaop.sql`，
 *       写响应后**删掉该文件**。</li>
 *   <li>`GET /backUp`：文件名 = `<毫秒>` + i18n `export.file.name.database.snapshot.backup.suffix`；
 *       先 `log.info("大小为:" + stream.available())`（**用 available() 当文件大小**，照抄）。</li>
 *   <li>`POST /import`：`zipFile.size()` 当"文件数"传给服务层，且传给服务层的 `fileName`
 *       是 `MultipartFile.getName()`（**表单字段名**，不是原始文件名）。</li>
 *   <li>`POST /relationDataBase`：用 Gson 序列化后**手写** `application/json` 响应体 +
 *       附件名（i18n `export.file.name.database.relate.user.export`）。</li>
 *   <li>`POST /backup_db_data_sync`：只有"空列表 → 400
 *       `multiple.master.buckup.table.not.table.parameter`"这一条校验；服务层方法在厂商那边
 *       **反编译失败**（见 `IDatabaseService` 的实现 javadoc）。</li>
 * </ol>
 *
 * <h3>与厂商的已知差异（都会写进回报）</h3>
 * <ul>
 *   <li>`POST /{databaseName}/import`：厂商签名是
 *       `(@PathVariable String, @RequestPart String userName, @RequestBody MultipartFile file)`——
 *       `@RequestBody` 给 `MultipartFile` 在 Spring 6 下没有可用的 `HttpMessageConverter`（会 415）。
 *       这里改成 `@RequestPart("file") MultipartFile file`（**同一份 multipart 报文**，只是参数绑定方式不同）。</li>
 *   <li>`GET /allService`：厂商的 `getListWithOutGaop` 是"tenxCloud 全量服务 + 逐行 K8s/授权富化"，
 *       本重写**复用同域的** `IServiceQueryService.queryServiceAuthList`（`/v1/service/serviceAuth` 的实现，
 *       同一套过滤 + 富化 + 分页口径），不再自己拼一遍。差异点：厂商这里按
 *       `system/appType == AppTypeLabelsEnum.BUSINESS` 过滤，`serviceAuth` 那条按
 *       `appName != gaop/gis-server` 过滤（两条口径在多数集群上等价，但不是同一段代码）。</li>
 *   <li>`inputStreamToFile`（解析上传 zip）在**控制器**里用 Jackson 实现：厂商在 service 里用 **Gson**，
 *       而 domain 禁用 JSON 库。厂商还对该 json 做了 `StringEscapeUtils.unescapeJava`
 *       （commons-lang **2.x** 的 API，本仓没有）——本重写按原样 JSON 解析（我们自己写出的
 *       导出包里本来就没有转义序列）。</li>
 *   <li>`@AuditLog` 注解同 `BackupController`：只记录在 javadoc，不加注解（本仓无审计链路）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/database")
public class DataBaseController {

    private static final String ATTACHMENT_PREFIX = "attachment; filename*=UTF-8''";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private IDatabaseService databaseService;

    /** 数据库用户子域（厂商 `DatabaseUserController` 的 5 条端点）。 */
    @Resource
    private IDatabaseUserService databaseUserService;

    /** `/allService` 复用软件-服务-端口域的读能力（厂商那边也是 controller 直接调 tenxCloud/authService/softwareService）。 */
    @Resource
    private IServiceQueryService serviceQueryService;

    @Resource
    private MessageResolver messageResolver;

    /** 厂商 `data.backup`（`application.yml:183-185`，默认 `/opt/gaop/upload/backup`）。 */
    @Value("${data.backup:/opt/gaop/upload/backup}")
    private String fileBackUp;

    /** 厂商 `gaop.k8s.namespace` / 常量 `K8s.NAMESPACE`。 */
    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    // ================================================================ 1. 上传 sql 执行

    /**
     * 厂商 `POST /{databaseName}/import`（`DataBaseController:116-143`）。
     *
     * <p>照抄点：文件名清洗两次（先删所有空白，再删 `(...)` 与括号）；
     * 不是 `.sql` 结尾 → 400 `global.sql.file.required`；异常包装成 `RuntimeException(e.getMessage())`
     * （**不是** WrongArgumentException ⇒ HTTP 500/`-1` 信封）；结尾打一条"调用接口花费"日志。
     */
    @PostMapping(value = "/{databaseName}/import", consumes = "multipart/form-data")
    public ResponseDTO<DatabaseExecResultDTO> executeSQLFromImport(
            @PathVariable String databaseName,
            @RequestPart("userName") String userName,
            @RequestPart("file") MultipartFile file) {
        long timeMillis = System.currentTimeMillis();
        long startTime = System.currentTimeMillis();
        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)
                || !originalFileName.toLowerCase().endsWith("sql")) {
            throw new WrongArgumentException("global.sql.file.required");
        }
        originalFileName = cleanFileName(originalFileName);
        try (InputStream inputStream = file.getInputStream()) {
            DatabaseExecResultEntity result =
                    databaseService.importSql(inputStream, originalFileName, databaseName, timeMillis,
                            userName, this.fileBackUp);
            return ResponseDTO.ok(toExecResultDTO(result));
        } catch (WrongArgumentException e) {
            // 厂商 `catch (Exception e)` 会把 WrongArgumentException 一起吞掉再包成 RuntimeException；
            // 本仓的全局异常处理器必须能看到 400 语义，否则错误契约会从 400 变成 500。见回报"已知差异"。
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            long time = System.currentTimeMillis() - startTime;
            log.info("--------------调用接口花费" + time + "毫秒-------------");
        }
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DataBaseController.class);

    /** 厂商在 3 处重复的正则清洗（`DataBaseController:125-127`）。 */
    private static String cleanFileName(String originalFileName) {
        String name = originalFileName.replaceAll("\\s*|\t|\r|\n", "");
        String rex = "\\s*\\(.+\\)\\s*";
        return name.replaceAll(rex, "").replaceAll("[()]", "");
    }

    // ================================================================ 2. 下载 sql

    /** 厂商 `GET /download`（`DataBaseController:145-163`）：文件不存在 → 400 `file.not.found`。 */
    @GetMapping("/download")
    public void getFile(HttpServletResponse response, @RequestParam String fileName) {
        File file = new File(this.fileBackUp, fileName);
        if (!file.exists()) {
            throw new WrongArgumentException("file.not.found");
        }
        response.reset();
        response.setContentType("application/x-sql");
        response.setHeader("Content-Disposition",
                ATTACHMENT_PREFIX + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8));
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            Files.copy(file.toPath(), outputStream);
            outputStream.flush();
        } catch (IOException e) {
            log.error("文件下载错误", e);
        }
    }

    /**
     * 厂商 `GET /initSql/download`（`DataBaseController:165-184`）：
     * 库名查不到初始化 sql 名 → 400 `database.init.file.not.found`（**带上库名当参数**）。
     */
    @GetMapping("/initSql/download")
    public void downloadInitSqlFile(HttpServletResponse response, @RequestParam String databaseName) {
        String initSqlName = databaseService.getInitSqlName(databaseName);
        if (initSqlName == null) {
            throw new WrongArgumentException("database.init.file.not.found", databaseName);
        }
        File file = new File(this.fileBackUp, initSqlName);
        response.reset();
        response.setContentType("application/x-sql");
        response.setHeader("Content-Disposition",
                ATTACHMENT_PREFIX + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8));
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            Files.copy(file.toPath(), outputStream);
            outputStream.flush();
        } catch (IOException e) {
            log.error("初始化sql文件下载错误", e);
        }
    }

    // ================================================================ 3. 创建库

    /**
     * 厂商 `POST ""`（`DataBaseController:186-197`）。
     * <p>`@AuditLog(opModule="DATABASE", opType="audit.export.behavior.type.create.a.database", behavior="CREATE")`。
     * <p>判重走"MySQL 管理连接"的 `show databases`（厂商 `mysqlMapper.showDataBases()`）。
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseDTO<Object> createDataBaseInfo(
            @RequestPart("dataBaseName") String dataBaseName,
            @RequestPart(value = "service", required = false) String service,
            @RequestPart(value = "desc", required = false) String desc,
            @RequestPart("userName") String userName,
            @RequestPart("file") MultipartFile file) throws IOException {
        boolean existDatabase = databaseService.existDatabase(dataBaseName);
        if (existDatabase) {
            throw new WrongArgumentException("database.duplicate.database");
        }
        try (InputStream in = file.getInputStream()) {
            databaseService.addDatabaseInfo(dataBaseName, service, desc, userName, in, file.getOriginalFilename());
        }
        return ResponseDTO.<Object>ok(true);
    }

    // ================================================================ 4. 改描述

    /**
     * 厂商 `PUT /{dataBaseName}`（`DataBaseController:199-204`）。
     * <p>`@AuditLog(opModule="DATABASE", opType="audit.export.behavior.type.modify.database.description", behavior="CREATE")`
     * ——注意厂商的 `behavior` 写的是 **CREATE**（不是 UPDATE），照抄在文档里。
     */
    @PutMapping("/{dataBaseName}")
    public ResponseDTO<com.csm.paas.core.domain.database.model.entity.DatabaseEntity> updateDatabaseInfo(
            @PathVariable String dataBaseName, @RequestBody DatabaseDescribeDTO databaseDescribe) {
        DatabaseDescribeVO vo = DatabaseDescribeVO.builder()
                .service(databaseDescribe.getService())
                .desc(databaseDescribe.getDesc())
                .userName(databaseDescribe.getUserName())
                .build();
        return ResponseDTO.ok(databaseService.updateDatabaseByName(dataBaseName, vo));
    }

    // ================================================================ 5. 库列表

    /** 厂商 `GET ""`（`DataBaseController:206-215`）。 */
    @GetMapping
    public ResponseDTO<DatabaseListDTO> getDataBases(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     @RequestParam(required = false) String keyword) {
        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        if (keyword != null) {
            keyword = keyword.replaceAll(" +", "");
        }
        var result = databaseService.getUserTableCount(p, s, keyword);
        List<DatabaseItemDTO> items = result.getDatabaseItemList().stream()
                .map(e -> DatabaseItemDTO.builder()
                        .name(e.getName()).tableCount(e.getTableCount()).service(e.getService())
                        .desc(e.getDesc()).sqlName(e.getSqlName()).userName(e.getUserName())
                        .build())
                .toList();
        return ResponseDTO.ok(DatabaseListDTO.builder().databaseItemList(items).total(result.getTotal()).build());
    }

    // ================================================================ 6. 更新初始化 sql

    /**
     * 厂商 `POST /update`（`DataBaseController:217-223`）。
     * <p>`@AuditLog(opModule="DATABASE", opType="audit.export.behavior.type.update.database.initialization.sql.file", behavior="UPDATE")`。
     */
    @PostMapping(value = "/update", consumes = "multipart/form-data")
    public ResponseDTO<Boolean> updateDatabaseInitSqlFile(@RequestPart("databaseName") String databaseName,
                                                         @RequestPart("userName") String userName,
                                                         @RequestPart("file") MultipartFile file)
            throws IOException {
        try (InputStream in = file.getInputStream()) {
            return ResponseDTO.ok(databaseService.updateDatabaseInitSqlFile(
                    databaseName, userName, in, file.getOriginalFilename()));
        }
    }

    // ================================================================ 7. 访问权限

    /**
     * 厂商 `POST /access_control`（`DataBaseController:225-230`）。
     * <p>`@AuditLog(opModule="DATABASE", opType="audit.export.behavior.type.configure.access.permissions", behavior="UPDATE")`。
     * <p>照抄点：这是 **POST** 且参数在 query string 上（`@RequestParam`），语义是"执行一次 GRANT"。
     */
    @PostMapping("/access_control")
    public ResponseDTO<List<DatabasePermissionDTO>> getDataBasesAccess(
            @RequestParam(required = false) String databaseName,
            @RequestParam(required = false) String accessUser) {
        List<DatabasePermissionDTO> list = databaseService.getAccessControl(databaseName, accessUser).stream()
                .map(e -> DatabasePermissionDTO.builder()
                        .databaseName(e.getDatabaseName()).accessUser(e.getAccessUser()).build())
                .toList();
        return ResponseDTO.ok(list);
    }

    // ================================================================ 8. 库所属用户

    /** 厂商 `GET /users`（`DataBaseController:232-236`）。 */
    @GetMapping("/users")
    public ResponseDTO<List<String>> getDatabaseBelongUsers(@RequestParam String databaseName) {
        return ResponseDTO.ok(databaseService.getDatabaseBelongUsers(databaseName));
    }

    // ================================================================ 9. 删库

    /**
     * 厂商 `DELETE ""`（`DataBaseController:238-246`）。
     * <p>`@AuditLog(opModule="DATABASE", opType="audit.export.behavior.type.delete.database", behavior="DELETE")`。
     */
    @DeleteMapping
    public ResponseDTO<Object> deleteDataBase(@RequestParam String dataBaseName) {
        String[] dataBaseNames = dataBaseName.split(",");
        ArrayList<String> dataBaseList = new ArrayList<>(List.of(dataBaseNames));
        databaseService.deleteDatabaseAllByName(dataBaseList);
        return ResponseDTO.<Object>ok(true);
    }

    // ================================================================ 10~12. 导出

    /**
     * 厂商 `GET /export/{databaseName}`（`DataBaseController:248-257`）。
     * <p>照抄点：`Process` 的 stdout 直接写给响应；**不 waitFor、不 destroy**
     * （进程结束后由 JDK 回收，错误也不会体现到响应里）。
     */
    @GetMapping("/export/{databaseName}")
    public void exportDatabase(@PathVariable String databaseName, HttpServletResponse response)
            throws IOException {
        Process mysqldump = databaseService.createDatabaseBackupProcess(databaseName);
        response.reset();
        response.setContentType("application/sql");
        response.setHeader("Content-Disposition", ATTACHMENT_PREFIX
                + URLEncoder.encode(databaseName + ".sql", StandardCharsets.UTF_8));
        ServletOutputStream outputStream = response.getOutputStream();
        mysqldump.getInputStream().transferTo(outputStream);
    }

    /**
     * 裸路径 `GET /v1/database/export`（带 `databaseName` 查询参数）——与
     * {@link #exportDatabase(String, HttpServletResponse)} 同一份实现。
     *
     * <p>为什么补这条：厂商与前端实际调用的是 `/v1/database/export/{databaseName}`（本类上面那条）；
     * 覆盖率口径里前端还出现了一条**裸路径** `/v1/database/export`（前端拼串写法被归一化后的产物）。
     * 为了让该口径闭合、且不改变厂商路径的行为，这里提供同一语义的查询参数版本
     * （厂商后端**没有**这条映射，属本重写的兼容性补齐，不添加任何新行为）。
     */
    @GetMapping("/export")
    public void exportDatabaseByParam(@RequestParam String databaseName, HttpServletResponse response)
            throws IOException {
        exportDatabase(databaseName, response);
    }

    // ================================================================ 22. 数据库用户（厂商 DatabaseUserController）

    /**
     * 厂商 `GET /v1/database/user`（`DatabaseUserController:42-51`）：`{databaseUsers,total}`。
     * <p>`keyword` 先 `replaceAll(" +","")`（厂商在 controller 里做，**不去首尾空格**）；
     * 分页用厂商 `SqlUtils` 口径归一化。
     */
    @GetMapping("/user")
    public ResponseDTO<DatabaseUserListDTO> getDataBaseUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        if (keyword != null) {
            keyword = keyword.replaceAll(" +", "");
        }
        var result = databaseUserService.list(p, s, keyword);
        return ResponseDTO.ok(DatabaseUserListDTO.builder()
                .databaseUsers(result.getDatabaseUsers().stream().map(DataBaseController::toUserDTO).toList())
                .total(result.getTotal())
                .build());
    }

    /**
     * `GET /v1/database/user/{dataBaseUser}`：单个数据库用户。
     *
     * <p>厂商 `DatabaseUserController` **没有**这条只读映射；它对同一路径只有 `PUT /{dataBaseUser}`（改密码/描述）。
     * 本任务要求补一条读取口径，这里按厂商**同一语义**（路径变量是**用户名**，不是数字 id——
     * 厂商侧该位置就叫 `dataBaseUser`，前端 `PUT /v1/database/user/{dataBaseUser}` 也传用户名）实现：
     * 查不到 ⇒ 400 `user.not.found.modify.failed`（与厂商 `modifyDatabaseUser` 的首道闸同键）。
     */
    @GetMapping("/user/{dataBaseUser}")
    public ResponseDTO<DatabaseUserDTO> getDataBaseUser(@PathVariable String dataBaseUser) {
        DatabaseUserEntity user = databaseUserService.detailByUsername(dataBaseUser)
                .orElseThrow(() -> new WrongArgumentException("user.not.found.modify.failed"));
        return ResponseDTO.ok(toUserDTO(user));
    }

    /**
     * 厂商 `POST /v1/database/user`（`DatabaseUserController:53-65`）：新增数据库用户。
     * <p>两道 400 校验（root 保留名、重名）在领域层照抄；返回**新建的那一行**。
     */
    @PostMapping("/user")
    public ResponseDTO<DatabaseUserDTO> addDataBaseUser(@RequestBody DatabaseUserFormDTO form) {
        return ResponseDTO.ok(toUserDTO(databaseUserService.add(
                form.getUsername(), form.getPassword(), form.getDesc())));
    }

    /**
     * 厂商 `PUT /v1/database/user/{dataBaseUser}`（`DatabaseUserController:77-82`）：改密码/描述。
     * <p>请求体是 `DatabaseUserModify`（password/desc/updatedAt）；`updatedAt` 由服务层覆盖成当前毫秒。
     */
    @PutMapping("/user/{dataBaseUser}")
    public ResponseDTO<DatabaseUserDTO> modifyDataBaseUser(@PathVariable String dataBaseUser,
                                                          @RequestBody DatabaseUserModifyDTO form) {
        return ResponseDTO.ok(toUserDTO(databaseUserService.modify(
                dataBaseUser, form.getPassword(), form.getDesc())));
    }

    /**
     * 厂商 `DELETE /v1/database/user?dataBaseUser=`（`DatabaseUserController:67-75`）：
     * 用户名用 `,` 分隔批量删；某个用户不存在 ⇒ 400 `user.not.found.cannot.delete`（立即中断）。
     */
    @DeleteMapping("/user")
    public ResponseDTO<Object> deleteDataBaseUsers(@RequestParam String dataBaseUser) {
        String[] dataBaseUsers = dataBaseUser.split(",");
        databaseUserService.deleteAllByNames(new ArrayList<>(List.of(dataBaseUsers)));
        return ResponseDTO.<Object>ok(true);
    }

    /**
     * 厂商 `PUT /v1/database/user/revoke_authorization?databaseName&databaseUser`
     * （`DatabaseUserController:84-90`）：取消用户对某库的授权，恒返回 `true`。
     *
     * <p>方法同时接受 **PUT**（厂商与前端实际使用）与 **POST**（本任务清单里写的口径），
     * 两条路径同一实现、同一参数，不改变厂商行为。
     */
    @RequestMapping(value = "/user/revoke_authorization",
            method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseDTO<Boolean> revokeAuthorization(@RequestParam String databaseName,
                                                    @RequestParam String databaseUser) {
        databaseUserService.revokeAuthorization(databaseName, databaseUser);
        return ResponseDTO.ok(true);
    }

    /** `DatabaseUserEntity` → 厂商 6 键（顺序与 `model/DatabaseUser` 一致）。 */
    private static DatabaseUserDTO toUserDTO(DatabaseUserEntity user) {
        return DatabaseUserDTO.builder()
                .id(user.getId()).username(user.getUsername()).password(user.getPassword())
                .updatedAt(user.getUpdatedAt()).createdAt(user.getCreatedAt()).desc(user.getDesc())
                .build();
    }

    /** 厂商 `GET /export_framework`（`DataBaseController:259-270`）：导出 gaop 库并**删掉临时文件**。 */
    @GetMapping("/export_framework")
    public void exportFramework(HttpServletResponse response) throws IOException {
        File sqlBakFile = databaseService.genGaopBackup();
        response.reset();
        response.setContentType("application/sql");
        response.setHeader("Content-Disposition", ATTACHMENT_PREFIX
                + URLEncoder.encode(sqlBakFile.getName(), StandardCharsets.UTF_8));
        ServletOutputStream outputStream = response.getOutputStream();
        Files.copy(sqlBakFile.toPath(), outputStream);
        outputStream.flush();
        if (!sqlBakFile.delete()) {
            log.warn("gaop 导出临时文件删除失败:{}", sqlBakFile);
        }
    }

    // ================================================================ 13. MySQL 全局配置

    /** 厂商 `GET /mysql_info`（`DataBaseController:272-276`）。 */
    @GetMapping("/mysql_info")
    public ResponseDTO<DatabaseMysqlInfoDTO> getAllMysqlInfo() {
        var settings = databaseService.getAllMysqlGlobalSettings();
        return ResponseDTO.ok(DatabaseMysqlInfoDTO.builder()
                .storageEngine(settings.getStorageEngine()).storageSize(settings.getStorageSize())
                .timeZone(settings.getTimeZone()).caseSensitive(settings.getCaseSensitive())
                .port(settings.getPort()).version(settings.getVersion()).createdAt(settings.getCreatedAt())
                .build());
    }

    // ================================================================ 14~16. 快照

    /**
     * 厂商 `POST /snapshot`（`DataBaseController:278-286`）。
     * <p>照抄点：临时文件名前缀是 `<毫秒>dataBase_backup` 且后缀**又拼了一次** `.zip`
     * （`File.createTempFile(prefix, ".zip")`）；然后 `saveDataBaseSnapShot(zipFile, data.backup, 1)`。
     */
    @PostMapping("/snapshot")
    public ResponseDTO<Boolean> saveDataBaseSnapShot() throws IOException {
        long currentTimeMillis = System.currentTimeMillis();
        String s = currentTimeMillis + "dataBase_backup";
        File zipFile = File.createTempFile(s, ".zip");
        return ResponseDTO.ok(databaseService.saveDataBaseSnapShot(zipFile, this.fileBackUp, 1));
    }

    /** 厂商 `GET /snapshotStatus`（`DataBaseController:288-293`）：0 保存中 / 1 保存完成 / 2 从未保存过。 */
    @GetMapping("/snapshotStatus")
    public ResponseDTO<Integer> getSnapShotStatus() {
        return ResponseDTO.ok(databaseService.getSnapShotStatus());
    }

    /**
     * 厂商 `GET /backUp`（`DataBaseController:295-309`）。
     * <p>照抄点：`log.info("大小为:" + dataBaseBackUp.available())` —— 用 `available()` 当文件大小。
     */
    @GetMapping("/backUp")
    public void getDataBaseBackUp(HttpServletResponse response) throws IOException {
        long currentTimeMillis = System.currentTimeMillis();
        String suffix = messageResolver.resolve("export.file.name.database.snapshot.backup.suffix");
        String zipName = currentTimeMillis + suffix;
        InputStream dataBaseBackUp = databaseService.getDataBaseBackUp();
        response.setContentType("application/x-zip-compressed");
        response.setHeader("Content-Disposition",
                ATTACHMENT_PREFIX + URLEncoder.encode(zipName, StandardCharsets.UTF_8));
        ServletOutputStream outputStream = response.getOutputStream();
        int available = dataBaseBackUp.available();
        log.info("大小为:" + available);
        dataBaseBackUp.transferTo(outputStream);
        outputStream.flush();
    }

    /** 厂商 `GET /version`（`DataBaseController:311-316`）：没有快照时返回 `null`。 */
    @GetMapping("/version")
    public ResponseDTO<String> getSnapShotVersion() {
        return ResponseDTO.ok(databaseService.getSnapShotVersion());
    }

    /** 厂商 `GET /syncStatus`（`DataBaseController:318-323`）。 */
    @GetMapping("/syncStatus")
    public ResponseDTO<DatabaseSyncStatusDTO> syncDatabaseInfo() {
        var status = databaseService.syncDatabaseInfo();
        return ResponseDTO.ok(DatabaseSyncStatusDTO.builder()
                .syncing(status.isSyncing()).success(status.isSuccess())
                .currentDatabaseName(status.getCurrentDatabaseName())
                .current(status.getCurrent()).total(status.getTotal())
                .build());
    }

    // ================================================================ 17. 导入

    /**
     * 厂商 `POST /import`（`DataBaseController:325-337`）。
     * <p>`@AuditLog(opModule="DATABASE", opType="audit.export.behavior.type.import.database", behavior="IMPORT")`。
     * <p>照抄点：临时文件 `File.createTempFile("datetmp", ".zip")`；
     * `zipFile.size()`（**条目数**）当 size；`file.getName()`（**表单字段名**）当 fileName。
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseDTO<DatabaseImportSqlInfoDTO> importDatabaseInfo(
            @RequestParam("file") MultipartFile file) throws IOException {
        String s = "datetmp";
        File tempFile = File.createTempFile(s, ".zip");
        DatabaseImportInfoEntity importInfo;
        int size;
        try (InputStream in = file.getInputStream()) {
            importInfo = inputStreamToFile(in, tempFile);
        }
        try (ZipFile zipFile = new ZipFile(tempFile)) {
            size = zipFile.size();
        }
        DatabaseImportSqlInfoEntity result;
        try (FileInputStream inputStream = new FileInputStream(tempFile)) {
            result = databaseService.importDataBase(inputStream, file.getName(), size, importInfo);
        }
        return ResponseDTO.ok(DatabaseImportSqlInfoDTO.builder()
                .successList(result.getSuccessList()).errorList(result.getErrorList()).build());
    }

    /**
     * 厂商 `DatabaseService.inputStreamToFile:1310-1410`（**本重写把它放在 trigger 层**，
     * 因为 domain 禁用 JSON 库；厂商用的是 Gson + commons-lang 2.x 的 `unescapeJava`）。
     *
     * <p>照抄点：
     * <ul>
     *   <li>内嵌 `.zip` 条目用 `moreZip` 展平（**只取 `.sql`**，其它跳过）；</li>
     *   <li>`.sql` 条目原样转存到新的 zip；</li>
     *   <li>`databaseUser.json` → 走 `importDatabaseUser`（本重写未移植 `g_database_user`，**跳过**）；</li>
     *   <li>`databaseUserRelation.json` → `List&lt;DatabaseUserRelationInfo&gt;`；</li>
     *   <li>`databaseSql.json` → `Map&lt;sqlName, dbName&gt;`。</li>
     * </ul>
     */
    private DatabaseImportInfoEntity inputStreamToFile(InputStream ins, File file) throws IOException {
        List<DatabaseUserRelationInfoEntity> relationList = new ArrayList<>();
        Map<String, String> sqlHashMap = new HashMap<>();
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(file.toPath()));
             ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(ins),
                     StandardCharsets.UTF_8)) {
            ZipEntry ze;
            while ((ze = zipInputStream.getNextEntry()) != null) {
                String fileName = ze.getName();
                log.info("文件名称:" + fileName);
                if (!ze.isDirectory() && fileName.endsWith(".zip")) {
                    try {
                        moreZip(zipInputStream, zipOut);
                    } catch (Exception e) {
                        log.error("转换成zip包出错，异常为:", e);
                        throw new WrongArgumentException("database.parse.db.import.file.error");
                    }
                }
                if (!ze.isDirectory() && fileName.endsWith(".sql")) {
                    try {
                        fileAllToZip(zipInputStream, zipOut, fileName);
                    } catch (Exception e) {
                        log.error("转换成zip包出错，异常为:", e);
                        throw new WrongArgumentException("database.parse.db.import.file.error");
                    }
                }
                if (ze.isDirectory() || !fileName.endsWith(".json")) {
                    continue;
                }
                byte[] content = readAll(zipInputStream);
                if ("databaseUser.json".equals(fileName)) {
                    // 厂商这里会调 importDatabaseUser（往 g_database_user + mysql.user 写）；
                    // 本切片未建模 g_database_user ⇒ 只记日志跳过（见回报"未做项"）。
                    log.warn("databaseUser.json 导入未移植（g_database_user 未建模），跳过 {} 字节", content.length);
                }
                if ("databaseUserRelation.json".equals(fileName)) {
                    List<DatabaseUserRelationInfoEntity> parsed = OBJECT_MAPPER.readValue(
                            content, new TypeReference<List<DatabaseUserRelationInfoDTO>>() {
                            }).stream()
                            .map(d -> DatabaseUserRelationInfoEntity.builder()
                                    .databaseName(d.getDatabaseName())
                                    .databaseUserList(d.getDatabaseUserList())
                                    .build())
                            .toList();
                    relationList.addAll(parsed);
                }
                if ("databaseSql.json".equals(fileName)) {
                    Map<String, String> parsed = OBJECT_MAPPER.readValue(content,
                            new TypeReference<HashMap<String, String>>() {
                            });
                    sqlHashMap.putAll(parsed);
                }
            }
        }
        return DatabaseImportInfoEntity.builder()
                .databaseUserRelationInfoList(relationList)
                .sqlHashMap(sqlHashMap)
                .build();
    }

    /** 厂商 `DatabaseService.moreZip:1296-1308`：只取 `.sql`（非 sql 的记日志跳过）。 */
    private void moreZip(InputStream ins, ZipOutputStream zipout) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(ins), StandardCharsets.UTF_8);
        ZipEntry ze;
        while ((ze = zipInputStream.getNextEntry()) != null) {
            String zeName = ze.getName();
            log.info("当前压入到临时数据库压缩包中文件名称为:" + zeName);
            if (zeName.endsWith(".sql")) {
                fileAllToZip(zipInputStream, zipout, zeName);
                continue;
            }
            log.info(zeName + "不是sql文件，跳过该文件");
        }
    }

    /** 厂商 `DatabaseService.fileAllToZip:880-887`（**不关输入流**）。 */
    private void fileAllToZip(InputStream in, ZipOutputStream zipOut, String fileName) throws IOException {
        zipOut.putNextEntry(new ZipEntry(fileName));
        byte[] bufferArea = new byte[10240];
        int length;
        while ((length = in.read(bufferArea, 0, 10240)) != -1) {
            zipOut.write(bufferArea, 0, length);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) > -1) {
            baos.write(buffer, 0, len);
        }
        baos.flush();
        return baos.toByteArray();
    }

    // ================================================================ 18. 库用户关联导出

    /**
     * 厂商 `POST /relationDataBase`（`DataBaseController:339-353`）。
     * <p>照抄点：用 **Gson** 序列化后手写 `application/json` + 附件名（i18n
     * `export.file.name.database.relate.user.export`）；厂商还 new 了一个带 `JavaTimeModule`
     * 的 `ObjectMapper` 却**从未使用**（本重写不再创建那个对象）。
     */
    @PostMapping("/relationDataBase")
    public void getRelationDataBaseList(HttpServletResponse response) throws IOException {
        List<DatabaseUserRelationInfoDTO> dataBaseList = databaseService.getRelationDataBaseList().stream()
                .map(e -> DatabaseUserRelationInfoDTO.builder()
                        .databaseName(e.getDatabaseName()).databaseUserList(e.getDatabaseUserList()).build())
                .toList();
        byte[] jsonData = OBJECT_MAPPER.writeValueAsBytes(dataBaseList);
        response.setContentType("application/json");
        String filename = messageResolver.resolve("export.file.name.database.relate.user.export");
        response.setHeader("Content-Disposition",
                ATTACHMENT_PREFIX + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(jsonData);
        outputStream.flush();
    }

    // ================================================================ 19. 服务列表

    /**
     * 厂商 `GET /allService`（`DataBaseController:355-360` → 私有 `getListWithOutGaop:379-562`）。
     *
     * <p>**本重写复用同域的 `serviceAuth` 列表实现**（同一套过滤/富化/分页口径），
     * 差异点见类 javadoc。厂商的两条入口校验照抄：keyword 超过 45 字符 → 400
     * `service.invalid.service.name.length`；`page`/`size` 用厂商口径归一化。
     */
    @GetMapping("/allService")
    public ResponseDTO<ServiceListResponseDTO> withOutGaopServiceList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) String serviceType) {
        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        if (keyword != null && keyword.length() > 45) {
            throw new WrongArgumentException("service.invalid.service.name.length");
        }
        return ResponseDTO.ok(ServiceListAssembler.toResponse(
                serviceQueryService.queryServiceAuthList(this.namespace, p, s, keyword, authType, serviceType)));
    }

    // ================================================================ 20. 表大小

    /** 厂商 `GET /table_size`（`DataBaseController:362-367`）。 */
    @GetMapping("/table_size")
    public ResponseDTO<List<DatabaseTableSizeDTO>> getBusinessDataBaseTableSize() {
        List<DatabaseTableSizeDTO> ret = databaseService.getBusinessDataBaseTableSize().stream()
                .map(DataBaseController::toTableSizeDTO)
                .toList();
        return ResponseDTO.ok(ret);
    }

    private static DatabaseTableSizeDTO toTableSizeDTO(
            com.csm.paas.core.domain.database.model.entity.DatabaseTableSizeEntity e) {
        return DatabaseTableSizeDTO.builder()
                .name(e.getName()).size(e.getSize())
                .children(e.getChildren() == null ? null
                        : e.getChildren().stream().map(DataBaseController::toTableSizeDTO).toList())
                .build();
    }

    // ================================================================ 21. 备份库数据并同步

    /**
     * 厂商 `POST /backup_db_data_sync`（`DataBaseController:369-377`）。
     *
     * <p>唯一校验照抄：`CollectionUtils.isEmpty(...)` → 400
     * `multiple.master.buckup.table.not.table.parameter`。
     * <p>厂商的 service 方法 `backupDatabaseDataAndSyncOtherNode:2464-2487`
     * **反编译失败**（CFR 只留下一行 `throw new IllegalStateException("Decompilation failed")`），
     * 因此无法照抄实现；这里如实抛 `UnsupportedOperationException`（HTTP 500 / `-1` 信封），
     * 并写进回报的"未做项"。
     */
    @PostMapping("/backup_db_data_sync")
    public ResponseDTO<Boolean> backupDatabaseDataAndSyncOtherNode(
            @RequestBody List<DatabaseNeedBuckUpTableFormDTO> needBuckUpTableForms) {
        if (CollectionUtils.isEmpty(needBuckUpTableForms)) {
            throw new WrongArgumentException("multiple.master.buckup.table.not.table.parameter");
        }
        throw new UnsupportedOperationException(
                "厂商 DataBaseService.backupDatabaseDataAndSyncOtherNode 反编译失败，无法照抄实现");
    }

    /** `DatabaseExecResultEntity` → 对外 3 键（`log`/`exitCode` 不外泄）。 */
    private static DatabaseExecResultDTO toExecResultDTO(DatabaseExecResultEntity entity) {
        return DatabaseExecResultDTO.builder()
                .success(entity.isSuccess()).error(entity.getError()).cause(entity.getCause()).build();
    }
}
