package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.PublicAuthingDTO;
import com.csm.paas.core.api.dto.PublicAuthingResultDTO;
import com.csm.paas.core.api.dto.PublicResourceFormDTO;
import com.csm.paas.core.api.dto.PublicServiceStatusDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.cluster.model.valobj.PubResourceSpecVO;
import com.csm.paas.core.domain.database.model.entity.DatabaseImportInfoEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserRelationInfoEntity;
import com.csm.paas.core.domain.database.service.IDatabaseService;
import com.csm.paas.core.domain.node.service.IPubAuthingService;
import com.csm.paas.core.domain.node.service.IPubImportSupport;
import com.csm.paas.core.domain.node.service.IPubResourcePatchService;
import com.csm.paas.core.domain.node.service.IPubServiceStatusService;
import com.csm.paas.core.domain.role.service.IRoleImportService;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.domain.subrole.service.ISubRoleImportService;
import com.csm.paas.core.domain.user.service.IUserImportService;
import com.csm.paas.core.trigger.assembler.RoleImportExcelParser;
import com.csm.paas.core.trigger.assembler.SubRoleImportExcelParser;
import com.csm.paas.core.trigger.assembler.UserImportExcelParser;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 厂商 `PublicController` 里本批剩余的公共端点（`/v1/pub`）。
 *
 * <p>为什么另起一个控制器：`PublicController.java`（本仓已有的那条 `/nodePortUsed`）由别的 agent 持有，
 * 不允许改动；本类的类级前缀与它一致（`/v1/pub`），但方法路径互不重叠。
 *
 * <p>本类实现的 5 条（厂商出处逐条列出）：
 * <ul>
 *   <li>`POST /authing` → `PublicController.authing:1961-2027`；</li>
 *   <li>`POST /import`（multipart）→ `PublicController.importFullBackUp:3369-3576`；</li>
 *   <li>`GET /service_status` → `PublicController.allServiceStatus:442-459`；</li>
 *   <li>`GET /serviceStatus` → `PublicController.getServiceStatusInfo:2428-2585`；</li>
 *   <li>`POST /patchResource` → `PublicFdfsController.patchServiceResource:196-202`
 *       （实现是 `FileService.patchServiceResource:1138-1233`）。</li>
 * </ul>
 *
 * <p><b>类 C：本环境不可验证</b>——`/authing` 的"重启已授权服务"、`/import` 的整站还原都依赖
 * 真实集群/数据库；报文与契约照抄，在线只验"坏输入的错误文案"。
 *
 * <p><b>`POST /import` 的两处已声明缺口</b>（详见 `IPubImportSupport` javadoc）：
 * `software.json`（`SoftwareService.importSoftwareInfo` 未移植）与 `databaseUser.json`
 * （`g_database_user` 未建模）——两者都只记日志 + 写一条 error 级恢复日志后跳过，**不伪造成功**。
 */
@Slf4j
@RestController
@RequestMapping("/v1/pub")
public class PublicExtraController {

    /** 厂商用 Jackson 解析 `databaseUserRelation.json`（`databaseSql.json` 用 Gson）。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private IPubAuthingService pubAuthingService;

    @Resource
    private IPubServiceStatusService pubServiceStatusService;

    @Resource
    private IPubResourcePatchService pubResourcePatchService;

    @Resource
    private IPubImportSupport pubImportSupport;

    @Resource
    private IUserImportService userImportService;

    @Resource
    private IRoleImportService roleImportService;

    @Resource
    private ISubRoleImportService subRoleImportService;

    /** `configmap.json` 与厂商一样交给"软件域"的配置组导入能力（本仓已有 `importConfigMaps`）。 */
    @Resource
    private IServiceQueryService serviceQueryService;

    @Resource
    private IDatabaseService databaseService;

    /** 厂商 `K8s.NAMESPACE`（`configmap.json` 写入的命名空间）。 */
    @org.springframework.beans.factory.annotation.Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    // ------------------------------------------------------------------ 1. authing

    /**
     * 厂商 `PublicController.authing:1961-2027`（源码在该方法上带审计注解，opModule=AUTH）。
     * <p>审计切面（AuditLog）在本仓尚未实现，故不写该注解——不影响响应契约。
     */
    @PostMapping("/authing")
    public ResponseDTO<List<PublicAuthingResultDTO>> authing(@RequestBody PublicAuthingDTO authingParameters) {
        List<PublicAuthingResultDTO> list = pubAuthingService.authing(authingParameters.getAuthToken()).stream()
                .map(r -> PublicAuthingResultDTO.builder().serviceId(r.getServiceId()).result(r.isResult()).build())
                .toList();
        return ResponseDTO.ok(list);
    }

    // ------------------------------------------------------------------ 2. service_status

    /** 厂商 `PublicController.allServiceStatus:442-459`。 */
    @GetMapping("/service_status")
    public ResponseDTO<PublicServiceStatusDTO.ServiceStatusListDTO> allServiceStatus() {
        List<PublicServiceStatusDTO> serviceList = pubServiceStatusService.allServiceStatus().stream()
                .map(v -> PublicServiceStatusDTO.builder()
                        .serviceName(v.getServiceName()).status(v.getStatus()).build())
                .toList();
        return ResponseDTO.ok(PublicServiceStatusDTO.ServiceStatusListDTO.builder()
                .service(serviceList).build());
    }

    // ------------------------------------------------------------------ 3. serviceStatus

    /**
     * 厂商 `PublicController.getServiceStatusInfo:2428-2585`。
     *
     * <p>响应直接用 Map 承载厂商的 JSON 结构（键名与顺序照抄：`appstatus` / `sysInfo` / `radondbInfo`），
     * 与仓库里其它"原样透传 K8s 快照"的端点同一口径。
     */
    @GetMapping("/serviceStatus")
    public ResponseDTO<Map<String, Object>> serviceStatus() {
        return ResponseDTO.ok(pubServiceStatusService.serviceStatusInfo());
    }

    // ------------------------------------------------------------------ 4. patchResource

    /**
     * 厂商 `PublicFdfsController.patchServiceResource:196-202`（body 是 `ResourceForm`）。
     *
     * <p>照抄点：`requests` 无 gpu；`limits.gpu` 非空时才写 `nvidia.com/gpu`。
     */
    @PostMapping("/patchResource")
    public ResponseDTO<Boolean> patchServiceResource(@RequestBody PublicResourceFormDTO form) {
        PubResourceSpecVO spec = PubResourceSpecVO.builder()
                .name(form.getName())
                .limitCpu(form.getLimits().getCpu())
                .limitMemory(form.getLimits().getMemory())
                .limitGpu(form.getLimits().getGpu())
                .requestCpu(form.getRequests().getCpu())
                .requestMemory(form.getRequests().getMemory())
                .build();
        return ResponseDTO.ok(pubResourcePatchService.patchServiceResource(spec));
    }

    // ------------------------------------------------------------------ 5. import

    /**
     * 厂商 `PublicController.importFullBackUp:3369-3576`（`POST /import`，multipart `file`）。
     *
     * <p>逐段照抄：zip 用 **GB18030** 编码打开；`user.xls` / `role.xls` / `sub_role.xls` /
     * `software.json` / `configmap.json` / `databaseUser.json` / `databaseUserRelation.json` /
     * 任何 `.sql` 与 `databaseSql.json` 各一段；`.sql` 原样转存到临时 zip 后交给
     * `DatabaseService.importDataBase`；临时 zip 条目数为 0 但出现过 `databaseSql.json` 时，
     * 只推一次进度（照抄 `:3558-3574`，含 `skip = false` 的复位）。
     *
     * <p>照抄点（容易漏）：
     * <ul>
     *   <li>条目名比较用的是 `ze.toString()`，而 JDK 的 `ZipEntry.toString()` 就是 `getName()`——
     *       本仓显式写 `getName()`（等价）；</li>
     *   <li>`software.json` 与 `databaseUserRelation.json` 那两段的
     *       `sendRecoveryLog(..., jsonData, "error")`：**level 是 `error` 而内容其实是成功路径**（照抄）；</li>
     *   <li>`newZipEntry` 之间**不调 `closeEntry()`**（厂商靠下一次 `putNextEntry` 隐式关闭）——照抄；</li>
     *   <li>末尾 `else if (skip)` 分支里 `skip = false` 之后**没有别的用途**（死赋值），照抄。</li>
     * </ul>
     *
     * <p>两处**主动偏离**（都写进 finding 文档）：
     * <ol>
     *   <li>临时 zip / 输入流用 try-with-resources 关闭（厂商泄漏句柄）；</li>
     *   <li>不调用 `StringEscapeUtils.unescapeJava`——与仓库既有
     *       `DataBaseController.inputStreamToFile:496-554` 对**同一个** `databaseUserRelation.json`
     *       的处理保持一致（两个端点必须对同一份导出包给出同样的结果）。</li>
     * </ol>
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseDTO<Boolean> importFullBackUp(@RequestParam("file") MultipartFile file) throws IOException {
        boolean skip = false;
        pubImportSupport.beginImport();

        List<DatabaseUserRelationInfoEntity> databaseUserRelationInfoList = new ArrayList<>();
        File dbTemp = File.createTempFile("dbtemp", ".zip");
        Map<String, String> sqlHashMap = new HashMap<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(
                new BufferedInputStream(file.getInputStream()), Charset.forName("GB18030"));
             ZipOutputStream dbTempOut = new ZipOutputStream(new FileOutputStream(dbTemp))) {
            log.info("获取到zip内容");
            ZipEntry ze;
            while ((ze = zipInputStream.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                String entryName = ze.getName();
                if (!ze.isDirectory() && "user.xls".equals(entryName)) {
                    log.info("开始导入用户列表");
                    pubImportSupport.sendRecoveryLog("user", "开始导入用户列表", null, "info");
                    byte[] content = readAll(zipInputStream);
                    // 厂商 `new MockMultipartFile("user.xls", stream)` + `handleImportExcel(userExcel, Boolean.TRUE)`
                    // ⇒ isImportFullBackUp = true、isCover = true
                    userImportService.importUsers(UserImportExcelParser.parse(new ByteArrayInputStream(content)), true);
                    pubImportSupport.sendRecoveryLog("user", "完成导入用户列表", null, "info");
                }
                if (!ze.isDirectory() && "role.xls".equals(entryName)) {
                    log.info("开始导入战位列表信息");
                    pubImportSupport.sendRecoveryLog("role", "开始导入战位列表信息", null, "info");
                    byte[] content = readAll(zipInputStream);
                    // 厂商调 `roleService.handleImportExcel(roleExcel)` ⇒ 单参重载，isCover = false（`RoleService:848-849`）
                    roleImportService.importRoles(RoleImportExcelParser.parse(new ByteArrayInputStream(content)), false);
                    pubImportSupport.sendRecoveryLog("role", "完成导入战位列表信息", null, "info");
                }
                if (!ze.isDirectory() && "sub_role.xls".equals(entryName)) {
                    log.info("开始导入子角色列表信息");
                    pubImportSupport.sendRecoveryLog("sub_role", "开始导入子角色列表信息", null, "info");
                    byte[] content = readAll(zipInputStream);
                    // 厂商调 `subRoleService.handleImportExcel(mockExcel)` ⇒ 单参重载，**isCover = true**（`SubRoleServiceImpl:535-536`）
                    subRoleImportService.importSubRoles(
                            SubRoleImportExcelParser.parse(new ByteArrayInputStream(content)), true);
                    pubImportSupport.sendRecoveryLog("sub_role", "完成导入子角色列表信息", null, "info");
                }
                if (!ze.isDirectory() && "software.json".equals(entryName)) {
                    log.info("开始导入软件与服务配置");
                    pubImportSupport.sendRecoveryLog("software", "开始导入软件与服务配置", null, "info");
                    String jsonData = readUtf8(zipInputStream, "software", "读取文件异常为", false);
                    // 厂商这一条 level 写的是 "error"（内容却是导入数据），照抄
                    pubImportSupport.sendRecoveryLog("software", "读取软件与服务配置数据", jsonData, "error");
                    pubImportSupport.importSoftwareJson(jsonData);
                    pubImportSupport.sendRecoveryLog("software", "完成导入软件与服务配置", null, "info");
                }
                if (!ze.isDirectory() && "configmap.json".equals(entryName)) {
                    log.info("开始导入配置组");
                    pubImportSupport.sendRecoveryLog("configmap", "开始导入配置组", null, "info");
                    byte[] content = readAll(zipInputStream);
                    // 厂商 `softwareService.importConfigMap(in)`：**存在就 replace、否则 create**（`:3363-3403`）
                    List<Map<String, Object>> configMaps = OBJECT_MAPPER.readValue(content,
                            new TypeReference<List<Map<String, Object>>>() {
                            });
                    serviceQueryService.importConfigMaps(namespace, configMaps);
                    pubImportSupport.sendRecoveryLog("configmap", "完成导入配置组", null, "info");
                }
                if (!ze.isDirectory() && "databaseUser.json".equals(entryName)) {
                    pubImportSupport.sendRecoveryLog("database", "开始导入数据库用户", entryName, "info");
                    byte[] content = readAll(zipInputStream);
                    pubImportSupport.importDatabaseUserJson(content);
                    pubImportSupport.sendRecoveryLog("database", "完成导入数据库用户", null, "info");
                }
                if (!ze.isDirectory() && "databaseUserRelation.json".equals(entryName)) {
                    log.info("开始获取数据库用户与数据库信息");
                    pubImportSupport.sendRecoveryLog("database", "开始获取数据库用户与数据库信息", entryName, "info");
                    String jsonData = readUtf8(zipInputStream, "database", "读取获取数据库用户与数据库信息文件异常为", true);
                    List<DatabaseUserRelationInfoEntity> fromJson = OBJECT_MAPPER.readValue(jsonData,
                            new TypeReference<List<DatabaseUserRelationInfoEntity>>() {
                            });
                    databaseUserRelationInfoList.addAll(fromJson);
                    pubImportSupport.sendRecoveryLog("database", "完成还原数据库用户与数据库信息", jsonData, "info");
                    log.info("结束获取数据库用户与数据库信息");
                }
                if (!ze.isDirectory() && entryName.endsWith(".sql")) {
                    log.info("读取到压缩包中sql文件:" + entryName + "开始加入到压缩包中");
                    pubImportSupport.sendRecoveryLog("database", "读取到压缩包中sql文件开始放入到压缩包中", entryName, "info");
                    fileAllToZip(zipInputStream, dbTempOut, entryName);
                    log.info("压缩sql文件:" + entryName + "到zip包中成功");
                    pubImportSupport.sendRecoveryLog("database", "压缩sql文件到zip包中成功", entryName, "info");
                }
                if (ze.isDirectory() || !entryName.endsWith("databaseSql.json")) {
                    continue;
                }
                skip = true;
                log.info("读取到压缩包中数据库sql文件关联数据库信息文件:" + entryName + "开始解析信息");
                pubImportSupport.sendRecoveryLog("database",
                        "读取到压缩包中数据库sql文件关联数据库信息文件，开始解析信息", entryName, "info");
                String jsonData = readUtf8(zipInputStream, "database", "读取获取数据库用户与数据库信息文件异常为", true);
                Map<String, String> parsed = OBJECT_MAPPER.readValue(jsonData,
                        new TypeReference<HashMap<String, String>>() {
                        });
                sqlHashMap = parsed;
            }
            zipInputStream.closeEntry();
        } catch (IOException e) {
            // 厂商在 databaseUserRelation.json 读取失败时把 RECOVERY 复位（`:3505-3510`）
            pubImportSupport.setRecovery(false);
            throw e;
        }

        String dbName = "dataBase_backup.zip";
        try (FileInputStream inputStream = new FileInputStream(dbTemp);
             ZipFile zipFile = new ZipFile(dbTemp)) {
            Integer size = zipFile.size();
            if (size != 0) {
                pubImportSupport.sendRecoveryLog("database", "导入数据信息", toJson(sqlHashMap), "info");
                // 厂商把返回值赋给一个没用的局部变量（`:3557`）；本实现同样丢弃
                databaseService.importDataBase(inputStream, dbName, size, DatabaseImportInfoEntity.builder()
                        .databaseUserRelationInfoList(databaseUserRelationInfoList)
                        .sqlHashMap(sqlHashMap)
                        .build());
            } else if (skip) {
                pubImportSupport.sendDatabaseImportProgress(true, false, 0, 1);
                pubImportSupport.sendRecoveryLog("database", "当前进度",
                        "{\"syncing\":true,\"success\":false,\"current\":0,\"total\":1}", "info");
                pubImportSupport.sendDatabaseImportProgress(false, true, 1, 1);
                pubImportSupport.sendRecoveryLog("database", "当前进度",
                        "{\"syncing\":false,\"success\":true,\"current\":1,\"total\":1}", "info");
                skip = false;
            }
        }
        return ResponseDTO.ok(true);
    }

    // ------------------------------------------------------------------ 工具

    /** 厂商 `PublicController.fileAllToZip:3360-3367`（**不**显式 `closeEntry`，靠下一次 putNextEntry 隐式关闭）。 */
    private void fileAllToZip(InputStream in, ZipOutputStream zipOut, String fileName) throws IOException {
        byte[] bufferArea = new byte[10240];
        zipOut.putNextEntry(new ZipEntry(fileName));
        int length;
        while ((length = in.read(bufferArea, 0, 10240)) != -1) {
            zipOut.write(bufferArea, 0, length);
        }
    }

    /** 厂商那段 `new byte[1024]` 的读循环（`:3398-3402` 等）。 */
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

    /**
     * 厂商那两段"读成 UTF-8 文本"的代码（`:3446-3458` 的 software.json、`:3497-3510` 的
     * databaseUserRelation.json、`:3534-3546` 的 databaseSql.json）。
     *
     * <p>照抄点：`databaseUserRelation.json` 与 `databaseSql.json` 两处读失败会**把恢复开关复位**
     * （`BackupLogService.RECOVERY = false`），而 `software.json` 那处**不复位**。
     */
    private String readUtf8(InputStream in, String module, String errorMessage, boolean resetRecovery)
            throws IOException {
        try {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取文件异常为:", e);
            pubImportSupport.sendRecoveryLog(module, errorMessage, String.valueOf(e), "error");
            if (resetRecovery) {
                pubImportSupport.setRecovery(false);
            }
            throw new WrongArgumentException("file.read.failed");
        }
    }

    /** 恢复日志里的 `data` 字段：厂商对非字符串走 `JsonUtils.toStr`。 */
    private static String toJson(Object o) {
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
