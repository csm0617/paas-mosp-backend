package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.BackupDownloadFormDTO;
import com.csm.paas.core.api.dto.BackupFileInfoDTO;
import com.csm.paas.core.api.dto.BackupFileStatusDTO;
import com.csm.paas.core.api.dto.BackupItemDTO;
import com.csm.paas.core.api.dto.BackupListDTO;
import com.csm.paas.core.api.dto.BackupRecoveryLogDTO;
import com.csm.paas.core.api.dto.BackupSettingItemDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.backup.model.entity.BackupDownloadEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupPageEntity;
import com.csm.paas.core.domain.backup.service.IBackupService;
import com.csm.paas.core.domain.backup.service.impl.BackupServiceImpl;
import com.csm.paas.core.trigger.i18n.MessageResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 厂商 {@code controller/BackController.java}（`/v1/backup`）的重写版，**16 条端点一条不少**。
 *
 * <h3>与厂商逐条对齐的契约点</h3>
 * <ul>
 *   <li>`GET /list`：`page`/`size` 走厂商 {@code SqlUtils} 口径（见 {@code PageUtils}）；
 *       `keyword` 先 `replaceAll(" +","")`；返回 `{list,total}`，且列表里 `databaseName`
 *       被 i18n 文案 **`backup.list.name.<databaseKey>`** 覆盖（厂商 `BackService.i18n:196-205`）。
 *       — i18n 在 trigger 层做（domain 不依赖 MessageSource）。</li>
 *   <li>`POST ""`：`backupService.backupInfoToJob()`；任务已存在 → 400 `Backup job already exists.`</li>
 *   <li>`POST /base`、`POST /delete`、`POST /singleBaseData`、`POST /singleService`、`POST /reset`、
 *       `POST /singleReset`：返回 `{msg:"OK",code:0,data:<Boolean>}`。</li>
 *   <li>`GET /baseData`：**照抄厂商的怪实现**——同一个 `downloadBaseInfo()` 被调**两次**、
 *       第一次的结果只用来写 `Content-Length`，第二次才写响应体；`Content-Type` 是
 *       `application/x-zip-compressed`；文件名是 `<毫秒>_框架基础服务数据.zip`
 *       （厂商 `BackController:80-95`）。</li>
 *   <li>`POST /singleDownload`：请求头 `Range`（形如 `0-1000`，**不带 `bytes=`**）、body
 *       `{dataKey,fileName}`；响应头顺序/取值照抄厂商 `downloadBackFile:1336-1387`；
 *       未知 `dataKey` ⇒ **200 + 空 body**（厂商什么都不做）。</li>
 *   <li>`GET /backFileList` / `GET /backupSetting` / `GET /fileInfo` / `GET /backupLogs`（type=1）/
 *       `GET /recoveryLogs`（type=2）。</li>
 *   <li>`POST /changeSetting`：`name` 不存在 → 400 `database.name.not.found`。</li>
 * </ul>
 *
 * <h3>本重写的已知差异</h3>
 * <ul>
 *   <li>厂商在这些端点上打了 `@AuditLog(opModule="BACKUP", ...)`；paas-core 目前**没有审计落库链路**，
 *       按本仓既有做法只在 javadoc 里记录，不加注解（同 `ImageController` 的处理）。</li>
 *   <li>厂商的 `@Operation`/`@Tag`（springdoc）注解本仓控制器一律不用，契约不受影响。</li>
 *   <li>STOMP 进度推送（`/topic/backup`、`/topic/serviceImage`）未接 —— 见 `IBackupService` 的实现 javadoc。</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/backup")
public class BackupController {

    /** 厂商 `ResponseUtils.attachment`：`attachment; filename*=UTF-8''<urlencoded>`。 */
    private static final String ATTACHMENT_PREFIX = "attachment; filename*=UTF-8''";

    @Resource
    private IBackupService backupService;

    @Resource
    private MessageResolver messageResolver;

    /** 厂商 `@GetMapping("/list") getDatabaseBackupList`（`BackController:45-50`）。 */
    @GetMapping("/list")
    public ResponseDTO<BackupListDTO<BackupItemDTO>> getDatabaseBackupList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        BackupPageEntity<com.csm.paas.core.domain.backup.model.entity.BackupEntity> result =
                backupService.queryBackupList(page, size, keyword);
        List<BackupItemDTO> list = result.getList().stream()
                .map(BackupController::toItemDTO)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        // 厂商 BackService.i18n:196-205：用 i18n 文案覆盖 databaseName
        list.forEach(dto -> dto.setDatabaseName(
                messageResolver.resolve("backup.list.name." + dto.getDatabaseKey())));
        return ResponseDTO.ok(BackupListDTO.<BackupItemDTO>builder().list(list).total(result.getTotal()).build());
    }

    private static BackupItemDTO toItemDTO(com.csm.paas.core.domain.backup.model.entity.BackupEntity e) {
        return BackupItemDTO.builder()
                .id(e.getId()).databaseKey(e.getDatabaseKey()).databaseName(e.getDatabaseName())
                .databaseFilename(e.getDatabaseFilename()).size(e.getSize())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).msg(e.getMsg())
                .build();
    }

    /**
     * 厂商 `@PostMapping backupInfo`（`BackController:52-59`）。
     * <p>`@AuditLog(opModule="BACKUP", opType="audit.export.behavior.type.one.click.data.backup", behavior="BACKUP")`。
     * <p>厂商抛 `IOException, SchedulerException`；这里只声明 `IOException`（领域层不暴露 Quartz 类型）。
     */
    @PostMapping
    public ResponseDTO<Boolean> backupInfo() throws IOException {
        return ResponseDTO.ok(backupService.backupInfoToJob());
    }

    /**
     * 厂商 `@PostMapping("/base") backUpBaseInfo`（`BackController:61-67`）。
     * <p>`@AuditLog(opModule="BACKUP", opType="audit.export.behavior.type.one.click.basic.framework.data.backup", behavior="BACKUP")`。
     */
    @PostMapping("/base")
    public ResponseDTO<Boolean> backUpBaseInfo() throws IOException {
        return ResponseDTO.ok(backupService.backUpBaseInfo());
    }

    /**
     * 厂商 `@PostMapping("/delete") backupInfoDelete`（`BackController:69-76`）。
     * <p>`@AuditLog(opModule="BACKUP", opType="audit.export.behavior.type.cancel.one.click.data.backup", behavior="BACKUP")`。
     * <p>照抄厂商的 `System.out.println("执行完中断后恢复备份定时任务状态")`（**不改成 log**）。
     */
    @PostMapping("/delete")
    public ResponseDTO<Boolean> backupInfoDelete() {
        Boolean result = backupService.cancelBackupJob();
        System.out.println("执行完中断后恢复备份定时任务状态");
        return ResponseDTO.ok(result);
    }

    /**
     * 厂商 `@GetMapping("/baseData") getBaseDataInfo`（`BackController:78-95`）。
     *
     * <p>照抄点（**这是厂商的怪实现，不要"顺手修好"**）：
     * `downloadBaseInfo()` 被调两次；第一次的流被完整复制到临时文件、只用 `tempFile.length()`
     * 当 `Content-Length`；响应的字节来自**第二次**调用。厂商也没有关掉第一个输入流。
     */
    @GetMapping("/baseData")
    public void getBaseDataInfo(HttpServletResponse response) throws IOException {
        long currentTimeMillis = System.currentTimeMillis();
        String zipName = currentTimeMillis + "_框架基础服务数据.zip";
        File tempFile = File.createTempFile("tmp", "zip");
        InputStream inputStream = backupService.downloadBaseInfo();
        try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
            inputStream.transferTo(fileOutputStream);
        }
        InputStream dataBaseBackUp = backupService.downloadBaseInfo();
        response.setContentType("application/x-zip-compressed");
        response.setHeader("Content-Length", String.valueOf(tempFile.length()));
        response.setHeader("Content-Disposition",
                ATTACHMENT_PREFIX + URLEncoder.encode(zipName, StandardCharsets.UTF_8));
        ServletOutputStream outputStream = response.getOutputStream();
        dataBaseBackUp.transferTo(outputStream);
        dataBaseBackUp.close();
        outputStream.flush();
    }

    /**
     * 厂商 `@PostMapping("/singleDownload") downloadSingleFile`（`BackController:97-102`）。
     * <p>`@AuditLog(opModule="BACKUP", opType="audit.export.behavior.type.single.backup.file.data.export", behavior="DOWNLOAD")`。
     */
    @PostMapping("/singleDownload")
    public void downloadSingleFile(@RequestHeader(value = "Range", required = false) String range,
                                   @RequestBody BackupDownloadFormDTO downloadBackup,
                                   HttpServletResponse response) throws IOException {
        BackupDownloadEntity entity = backupService.prepareSingleDownload(range, downloadBackup.getDataKey(),
                downloadBackup.getFileName());
        if (entity == null) {
            // 厂商：未知 dataKey 什么都不做（200 + 空 body）
            return;
        }
        writeDownload(entity, response);
    }

    /**
     * 厂商 `downloadBackFile:1336-1387` 的响应头与字节流写法，逐条照抄：
     * <pre>
     * Content-Type: application/octet-stream
     * Accept-Ranges: bytes
     * Last-Modified / Expires: 用当前毫秒当日期头
     * Content-Range: bytes start-end/total
     * Access-Control-Expose-Headers: Content-Range   （addHeader，不是 setHeader）
     * Content-Length: fullLength
     * Content-Disposition: attachment; filename*=UTF-8''&lt;name&gt;
     * </pre>
     */
    private void writeDownload(BackupDownloadEntity entity, HttpServletResponse response) throws IOException {
        long timeMillis = System.currentTimeMillis();
        response.setHeader("Content-Type", "application/octet-stream");
        response.setHeader("Accept-Ranges", "bytes");
        response.setDateHeader("Last-Modified", timeMillis);
        response.setDateHeader("Expires", System.currentTimeMillis());
        response.setHeader("Content-Range", "bytes " + entity.getStart() + "-" + entity.getEnd()
                + "/" + entity.getTotal());
        response.addHeader("Access-Control-Expose-Headers", "Content-Range");
        response.setHeader("Content-Length", String.valueOf(entity.getFullLength()));
        response.setHeader("Content-Disposition", ATTACHMENT_PREFIX
                + URLEncoder.encode(entity.getAttachmentName(), StandardCharsets.UTF_8));
        BackupServiceImpl.writeRange(entity, response.getOutputStream());
        response.flushBuffer();
    }

    /**
     * 厂商 `@PostMapping("/singleBaseData") singleBackupByBase`（`BackController:104-110`）。
     * <p>`@AuditLog(opModule="BACKUP", opType="audit.export.behavior.type.backup.framework.basic.data.separately", behavior="BACKUP")`。
     */
    @PostMapping("/singleBaseData")
    public ResponseDTO<Boolean> singleBackupByBase(@RequestParam String key) throws IOException {
        return ResponseDTO.ok(backupService.singleBackupByBase(key));
    }

    /**
     * 厂商 `@PostMapping("/singleService") singleBackupByService`（`BackController:112-118`）。
     *
     * <p>照抄点：厂商拿到 `Future&lt;Boolean&gt;` 后**直接丢掉**，无条件 `Response.ok(true)`
     * ——也就是"提交了就返回成功"，不等备份结果。
     */
    @PostMapping("/singleService")
    public ResponseDTO<Boolean> singleBackupByService(@RequestParam String key) throws IOException {
        backupService.singleBackupByService(key);
        return ResponseDTO.ok(true);
    }

    /** 厂商 `@GetMapping("/backFileList") getBackFileList`（`BackController:120-125`）。 */
    @GetMapping("/backFileList")
    public ResponseDTO<BackupListDTO<BackupFileInfoDTO>> getBackFileList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        BackupPageEntity<com.csm.paas.core.domain.backup.model.entity.BackupFileInfoEntity> result =
                backupService.queryBackFileList(page, size);
        List<BackupFileInfoDTO> list = result.getList().stream()
                .map(e -> BackupFileInfoDTO.builder().key(e.getKey()).name(e.getName()).build())
                .toList();
        return ResponseDTO.ok(BackupListDTO.<BackupFileInfoDTO>builder().list(list).total(result.getTotal()).build());
    }

    /** 厂商 `@GetMapping("/backupSetting") getBackupSettingList`（`BackController:127-132`）。 */
    @GetMapping("/backupSetting")
    public ResponseDTO<BackupListDTO<BackupSettingItemDTO>> getBackupSettingList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        BackupPageEntity<com.csm.paas.core.domain.backup.model.entity.BackupSettingEntity> result =
                backupService.queryBackupSettings(page, size);
        List<BackupSettingItemDTO> list = result.getList().stream()
                .map(e -> BackupSettingItemDTO.builder()
                        .id(e.getId()).databaseName(e.getDatabaseName()).type(e.getType()).build())
                .toList();
        return ResponseDTO.ok(BackupListDTO.<BackupSettingItemDTO>builder().list(list).total(result.getTotal()).build());
    }

    /** 厂商 `@PostMapping("/changeSetting") changeBackupSetting`（`BackController:134-139`）。 */
    @PostMapping("/changeSetting")
    public ResponseDTO<Boolean> changeBackupSetting(@RequestParam String name, @RequestParam Integer type) {
        return ResponseDTO.ok(backupService.changeBackupSetting(name, type));
    }

    /** 厂商 `@GetMapping("/fileInfo") getBackupFileStatus`（`BackController:141-146`）。 */
    @GetMapping("/fileInfo")
    public ResponseDTO<BackupFileStatusDTO> getBackupFileStatus(@RequestParam String key,
                                                               @RequestParam String name) {
        var result = backupService.queryBackupFileStatus(key, name);
        return ResponseDTO.ok(BackupFileStatusDTO.builder()
                .fileName(result.getFileName()).size(result.getSize()).md5(result.getMd5()).build());
    }

    /**
     * 厂商 `@PostMapping("/reset") resetAll`（`BackController:148-154`）。
     * <p>`@AuditLog(opModule="BACKUP", opType="audit.export.behavior.type.one.click.reset", behavior="RESET")`。
     */
    @PostMapping("/reset")
    public ResponseDTO<Boolean> resetAll() {
        return ResponseDTO.ok(backupService.resetAll());
    }

    /**
     * 厂商 `@PostMapping("/singleReset") singleReset`（`BackController:156-162`）。
     * <p>`@AuditLog(opModule="BACKUP", opType="audit.export.behavior.type.rest.separately", behavior="RESET")`。
     */
    @PostMapping("/singleReset")
    public ResponseDTO<Boolean> singleReset(@RequestParam String key) {
        return ResponseDTO.ok(backupService.resetByKey(key));
    }

    /** 厂商 `@GetMapping("/backupLogs") getBackupLogs`（`BackController:164-169`，type 固定传 1）。 */
    @GetMapping("/backupLogs")
    public ResponseDTO<BackupListDTO<BackupRecoveryLogDTO>> getBackupLogs(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseDTO.ok(logs(backupService.queryLogs(page, size, 1)));
    }

    /** 厂商 `@GetMapping("/recoveryLogs") getRecoveryLogs`（`BackController:171-176`，type 固定传 2）。 */
    @GetMapping("/recoveryLogs")
    public ResponseDTO<BackupListDTO<BackupRecoveryLogDTO>> getRecoveryLogs(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseDTO.ok(logs(backupService.queryLogs(page, size, 2)));
    }

    private static BackupListDTO<BackupRecoveryLogDTO> logs(
            BackupPageEntity<com.csm.paas.core.domain.backup.model.entity.BackupRecoveryLogEntity> result) {
        List<BackupRecoveryLogDTO> list = result.getList().stream()
                .map(e -> BackupRecoveryLogDTO.builder()
                        .type(e.getType()).module(e.getModule()).message(e.getMessage())
                        .data(e.getData()).timestamp(e.getTimestamp()).build())
                .toList();
        return BackupListDTO.<BackupRecoveryLogDTO>builder().list(list).total(result.getTotal()).build();
    }
}
