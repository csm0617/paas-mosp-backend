package com.csm.paas.core.domain.backup.service.impl;

import com.csm.paas.core.domain.backup.adapter.port.IBaseDataExportPort;
import com.csm.paas.core.domain.backup.adapter.repository.IBackupRepository;
import com.csm.paas.core.domain.backup.model.entity.BackupDownloadEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupFileInfoEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupFileStatusEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupPageEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupRecoveryLogEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupSettingEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupStatusEntity;
import com.csm.paas.core.domain.backup.model.valobj.BackupFileRangeVO;
import com.csm.paas.core.domain.backup.service.BackupLogHolder;
import com.csm.paas.core.domain.backup.service.BackupStatusHolder;
import com.csm.paas.core.domain.backup.service.IBackupService;
import com.csm.paas.core.domain.database.adapter.repository.IDatabaseRepository;
import com.csm.paas.core.domain.database.model.entity.BusinessDatabaseNameEntity;
import com.csm.paas.core.domain.database.service.IDatabaseService;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * `/v1/backup` 的业务实现——逐条照抄厂商 {@code service/BackService.java}。
 *
 * <h3>照抄的怪点</h3>
 * <ol>
 *   <li>`getBackFileList:1524`：`total` 被设成 **`infoList.size()`（当页条数）**，不是全量条数。</li>
 *   <li>`getBackupSettingList:1560-1599`：先用 MySQL 库列表对齐 `g_backup_setting`
 *       （库多就删多余设置、库少就补 type=1 的设置），**然后才** `startPage` 查一次全表。</li>
 *   <li>`resetByKey:1723-1766`：除未知 key 外**永远返回 true**；`software`/`configmap`/`ingress`
 *       分支把各子调用结果赋给 `software` 变量后**丢弃**，`software`/`appService`/`whiteListService`
 *       的返回值都不参与最终判断。</li>
 *   <li>`backUpBaseInfo:373-378`：configmap 出错时 `updateBackUpByError` 传的却是 **ingress 的文件名**
 *       （`tcpandUdpFileName`）。</li>
 *   <li>`downloadSingleBackup`：未知 `dataKey` 时**什么都不做**（不抛错、不写响应）。</li>
 *   <li>`downloadBackFile:1338-1341`：header 里的 Range 若是 `0-1000` 这种形态，先补 `bytes=` 前缀再校验。</li>
 *   <li>`getRange:1215-1220`：`start == -1` ⇒ 按"最后 N 字节"解释；上界越界或缺失 ⇒ 截到 `size-1`。</li>
 * </ol>
 *
 * <h3>本实现的"缺的一段"（详见回报）</h3>
 * <ul>
 *   <li>**Quartz**：厂商用 `StdSchedulerFactory` + 具名 JobKey(`backupJob`/`group1`) /
 *       TriggerKey(`backupTrigger`/`triggerGroup1`)。本仓无 quartz 依赖且不得改 pom
 *       ⇒ 用**单线程 Executor + 进程内 jobExists 标记**复刻"同一时刻只允许一个一键备份任务 /
 *       可中断 / 可取消"的可观测语义（`POST /v1/backup` 重复调用仍返回 400 "Backup job already exists."）。</li>
 *   <li>**STOMP 进度推送**（`/topic/backup`、`/topic/serviceImage`、`/topic/saveDatabase`）未接：
 *       domain 不依赖 spring-messaging，且文件白名单不允许新增 trigger 广播器。</li>
 *   <li>**镜像备份**（`getServiceInputStream("image")` / `doServiceImageBackup`，从 Harbor 拉 `*.tar.gz`）
 *       未移植：需要 `ImageService` + `Harbor`（切片 7 的 Harbor 域），本切片如实跳过
 *       ⇒ 一键备份的 `total` 会少算镜像项（照抄时 `total` 的算法保留，只是 `getServiceRelationImage()` 返回空）。</li>
 *   <li>`resetByKey` 的 user/role/sub_role/software/configmap/ingress/image 六条分支依赖跨域能力
 *       （K8s ConfigMap 批量删除、Harbor 清空、用户/战位重置），本切片只接通 **database** 一条；
 *       其余分支如实写日志并返回 true（与厂商"忽略子结果"的最终返回值一致）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements IBackupService {

    /** 厂商 `BackService.getImageFileName` / `getBackFileList` 用的镜像文件后缀。 */
    private static final String IMAGE_SUFFIX = ".tar.gz";

    /** 厂商 `BackService.backupInfo:459` 的固定项数 5（user/role/software/ingress/configmap）。 */
    private static final int BASE_TOTAL = 5;

    private final IBackupRepository backupRepository;
    private final IBaseDataExportPort baseDataExportPort;
    private final IDatabaseRepository databaseRepository;
    private final IDatabaseService databaseService;

    /** 进程内单线程执行器——替代厂商的 Quartz（见类 javadoc）。 */
    private final ExecutorService jobExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "backup-job");
        t.setDaemon(true);
        return t;
    });

    /** 厂商 `Scheduler.checkExists(new JobKey("backupJob","group1"))`。 */
    private volatile boolean jobExists = false;

    @Value("${data.backup:/opt/gaop/upload/backup}")
    private String fileBackUp;

    @Value("${cicd.projectid:31fceaa9-0910-47fd-80bf-730f00a6933b}")
    private String cicdProjectId;

    // ================================================================ 读

    /** 厂商 `getDatabaseBackupList:180-194`。 */
    @Override
    public BackupPageEntity<BackupEntity> queryBackupList(Integer page, Integer size, String keyword) {
        String kw = keyword == null ? null : keyword.replaceAll(" +", "");
        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        String likeKeyword = (kw == null || kw.isEmpty()) ? null : "%" + kw + "%";
        long total = this.backupRepository.countBackups(likeKeyword);
        List<BackupEntity> list = this.backupRepository.queryBackupPage(likeKeyword, PageUtils.offset(p, s), s);
        return BackupPageEntity.<BackupEntity>builder().list(list).total(total).build();
    }

    /** 厂商 `getBackFileList:1491-1526`。 */
    @Override
    public BackupPageEntity<BackupFileInfoEntity> queryBackFileList(Integer page, Integer size) {
        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        List<BackupFileInfoEntity> list = new ArrayList<>();
        String gaopBaseFileName = "baseBackupInfo.zip";
        String databaseFileName = "dataBase_backup.zip";
        if (new File(this.fileBackUp, gaopBaseFileName).exists()) {
            list.add(BackupFileInfoEntity.builder().key("gaop").name(gaopBaseFileName).build());
        }
        if (new File(this.fileBackUp, databaseFileName).exists()) {
            list.add(BackupFileInfoEntity.builder().key("database").name(databaseFileName).build());
        }
        List<String> images = getImageFileName(this.fileBackUp, IMAGE_SUFFIX);
        for (String image : images) {
            list.add(BackupFileInfoEntity.builder().key("image").name(image).build());
        }
        List<BackupFileInfoEntity> infoList = list.stream()
                .skip((long) (p - 1) * s).limit(s).collect(Collectors.toList());
        // 照抄厂商 :1524：total = **当页条数**，不是全量
        return BackupPageEntity.<BackupFileInfoEntity>builder().list(infoList).total(infoList.size()).build();
    }

    /** 厂商 `getImageFileName:1528-1540`。 */
    private List<String> getImageFileName(String path, String endWith) {
        List<String> imageNames = new ArrayList<>();
        File[] files = new File(path).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(endWith)) {
                    imageNames.add(file.getName());
                }
            }
        }
        return imageNames;
    }

    /** 厂商 `getBackupSettingList:1557-1600`。 */
    @Override
    public BackupPageEntity<BackupSettingEntity> queryBackupSettings(Integer page, Integer size) {
        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        List<BusinessDatabaseNameEntity> databaseItems = this.databaseRepository.allDatabasesTableCount(null);
        List<String> dbNames = databaseItems.stream().map(BusinessDatabaseNameEntity::getName)
                .collect(Collectors.toList());
        if (dbNames.isEmpty()) {
            this.backupRepository.deleteAllSettings();
            return BackupPageEntity.<BackupSettingEntity>builder().list(new ArrayList<>()).total(0L).build();
        }
        List<BackupSettingEntity> backupSettingList = this.backupRepository.queryAllSettings();
        if (backupSettingList == null || backupSettingList.isEmpty()) {
            dbNames.forEach(d -> this.backupRepository.insertSetting(d, 1));
        } else {
            List<String> backupSettings = backupSettingList.stream().map(BackupSettingEntity::getDatabaseName)
                    .collect(Collectors.toList());
            List<String> diffrent = getDiffrent(dbNames, backupSettings);
            if (backupSettings.size() > dbNames.size()) {
                if (diffrent != null && !diffrent.isEmpty()) {
                    diffrent.forEach(this.backupRepository::deleteSetting);
                }
            } else if (diffrent != null && !diffrent.isEmpty()) {
                diffrent.forEach(d -> this.backupRepository.insertSetting(d, 1));
            }
        }
        List<BackupSettingEntity> settingInfo = this.backupRepository.querySettingPage(PageUtils.offset(p, s), s);
        long total = this.backupRepository.countSettings();
        return BackupPageEntity.<BackupSettingEntity>builder().list(settingInfo).total(total).build();
    }

    /** 厂商 `DatabaseService.getDiffrent:2150-2176`（HashMap 计数法，**逐字照抄**）。 */
    static List<String> getDiffrent(List<String> list1, List<String> list2) {
        HashMap<String, Integer> map = new HashMap<>(list1.size() + list2.size());
        ArrayList<String> diff = new ArrayList<>();
        List<String> maxList = list1;
        List<String> minList = list2;
        if (list2.size() > list1.size()) {
            maxList = list2;
            minList = list1;
        }
        for (String string : maxList) {
            map.put(string, 1);
        }
        for (String string : minList) {
            Integer cc = map.get(string);
            if (cc != null) {
                cc = cc + 1;
                map.put(string, cc);
                continue;
            }
            map.put(string, 1);
        }
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getValue() != 1) {
                continue;
            }
            diff.add(entry.getKey());
        }
        return diff;
    }

    /** 厂商 `changeBackupSetting:1602-1614`。 */
    @Override
    public boolean changeBackupSetting(String databaseName, Integer type) {
        List<BackupSettingEntity> settingList = this.backupRepository.queryAllSettings().stream()
                .filter(s -> s.getDatabaseName() != null && s.getDatabaseName().equals(databaseName)).toList();
        if (settingList.isEmpty()) {
            throw new WrongArgumentException("database.name.not.found", databaseName);
        }
        return this.backupRepository.updateSettingType(databaseName, type);
    }

    /** 厂商 `getBackupFileStatus:1616-1709`。 */
    @Override
    public BackupFileStatusEntity queryBackupFileStatus(String key, String name) {
        BackupFileStatusEntity status = new BackupFileStatusEntity();
        if ("database".equals(key)) {
            List<BackupEntity> list = this.backupRepository.queryByKey("database").map(List::of).orElse(List.of());
            // 照抄厂商 :1620 `list.get(0)`：没有该行就 IndexOutOfBounds（500/-1 信封）
            BackupEntity backup = list.get(0);
            String databaseFilename = backup.getDatabaseFilename();
            status.setFileName(databaseFilename);
            File databaseFile = new File(this.fileBackUp, databaseFilename);
            if (databaseFile.exists()) {
                status.setSize(databaseFile.length());
                String md5Path = this.fileBackUp + File.separator + "md5";
                File md5File = new File(md5Path, "dataBase_backup.txt");
                if (md5File.exists()) {
                    try {
                        String md5Str = Files.readString(md5File.toPath()).trim();
                        if (!md5Str.isEmpty()) {
                            log.info("获取到的md5的值" + md5Str);
                            status.setMd5(md5Str);
                        } else {
                            status.setMd5(null);
                        }
                    } catch (IOException e) {
                        log.error("文件读取出现异常，异常为:", e);
                        status.setMd5(null);
                    }
                } else {
                    status.setMd5(null);
                }
            } else {
                status.setSize(null);
                status.setMd5(null);
            }
            return status;
        }
        if ("image".equals(key)) {
            status.setFileName(name);
            File imageFile = new File(this.fileBackUp, name);
            if (imageFile.exists()) {
                status.setSize(imageFile.length());
                File md5File = new File(this.fileBackUp + File.separator + "md5", "service_image_backup.txt");
                if (md5File.exists()) {
                    try {
                        String info = Files.readString(md5File.toPath());
                        status.setMd5(extractImageMd5(info, name));
                    } catch (IOException e) {
                        log.error("文件读取异常:", e);
                        status.setMd5(null);
                    }
                } else {
                    status.setSize(null);
                    status.setMd5(null);
                }
            } else {
                status.setSize(null);
                status.setMd5(null);
            }
            return status;
        }
        return status;
    }

    /**
     * 厂商 `getBackupFileStatus:1687-1697` 用 **Gson** 把 `service_image_backup.txt` 反序列化成
     * `List&lt;ImageInfoJson&gt;` 再按 `group_imageName--tag.tar.gz` 比对取 md5。
     * <p>domain 禁用 Jackson/Gson ⇒ 这里用**正则**从同一份 JSON 文本里取 `<name>` 对应的 `md5`
     * （`ImageInfoJson` 的字段顺序是 `group,imageName,tag,md5`，由本仓写入方保证）。
     */
    private static String extractImageMd5(String json, String fileName) {
        Pattern p = Pattern.compile(
                "\\{\\s*\"group\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"imageName\"\\s*:\\s*\"([^\"]*)\"\\s*,"
                        + "\\s*\"tag\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"md5\"\\s*:\\s*\"([^\"]*)\"\\s*}");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String candidate = m.group(1) + "_" + m.group(2) + "--" + m.group(3) + ".tar.gz";
            if (candidate.equals(fileName)) {
                return m.group(4);
            }
        }
        return null;
    }

    /** 厂商 `getBackupLogs:1892-1910`。 */
    @Override
    public BackupPageEntity<BackupRecoveryLogEntity> queryLogs(Integer page, Integer size, int type) {
        List<BackupRecoveryLogEntity> backLogs = null;
        switch (type) {
            case 1 -> backLogs = BackupLogHolder.BACK_LOGS;
            case 2 -> backLogs = BackupLogHolder.RECOVERY_LOGS;
            default -> {
                // 厂商 switch 无 default：backLogs 保持 null，下面 `backLogs.size()` 直接 NPE（照抄）
            }
        }
        long total = backLogs.size();
        List<BackupRecoveryLogEntity> pageLogs = backLogs;
        if (page != null && size != null) {
            pageLogs = backLogs.stream().skip((long) (page - 1) * size).limit(size).collect(Collectors.toList());
        }
        return BackupPageEntity.<BackupRecoveryLogEntity>builder().list(pageLogs).total(total).build();
    }

    /** 厂商 `downloadSingleBackup:1228-1334`。 */
    @Override
    public BackupDownloadEntity prepareSingleDownload(String range, String dataKey, String fileName) {
        log.info("获取到的datakey:" + dataKey);
        if ("user".equals(dataKey)) {
            return selectAndRange(range, dataKey, "backup.server.user.data.not.found");
        }
        if ("role".equals(dataKey)) {
            return selectAndRange(range, dataKey, "backup.server.storage.data.not.found");
        }
        if ("sub_role".equals(dataKey)) {
            return selectAndRange(range, dataKey, "backup.server.storage.data.not.found");
        }
        if ("software".equals(dataKey)) {
            return selectAndRange(range, dataKey, "backup.server.software.data.not.found");
        }
        if ("ingress".equals(dataKey)) {
            return selectAndRange(range, dataKey, "backup.server.lb.data.not.found");
        }
        if ("configmap".equals(dataKey)) {
            return selectAndRange(range, dataKey, "backup.server.config.group.data.not.found");
        }
        if ("database".equals(dataKey)) {
            return selectAndRange(range, dataKey, "backup.server.business.db.not.found");
        }
        if ("image".equals(dataKey)) {
            File file = new File(this.fileBackUp, fileName);
            if (!file.exists()) {
                throw new WrongArgumentException("backup.server.image.not.found", fileName);
            }
            return buildDownload(range, fileName, file);
        }
        // 照抄：未知 dataKey 什么都不做
        return null;
    }

    /** 厂商 `downloadSingleBackup` 七个 key 共用的那段逻辑（`:1235-1325` 逐行重复 7 次）。 */
    private BackupDownloadEntity selectAndRange(String range, String dataKey, String notFoundKey) {
        List<BackupEntity> backups = this.backupRepository.queryByKey(dataKey).map(List::of).orElse(List.of());
        if (backups.isEmpty()) {
            throw new WrongArgumentException("backup.data.identifier.not.found");
        }
        BackupEntity backup = backups.get(0);
        String databaseFilename = backup.getDatabaseFilename();
        File file = new File(this.fileBackUp, databaseFilename);
        if (!file.exists()) {
            throw new WrongArgumentException(notFoundKey);
        }
        return buildDownload(range, databaseFilename, file);
    }

    /** 厂商 `downloadBackFile:1336-1387`（只做"选文件 + 算 Range"，写响应在 trigger 层）。 */
    private BackupDownloadEntity buildDownload(String range, String databaseFilename, File file) {
        if (range != null && !range.isBlank()) {
            range = "bytes=" + range;
            log.info("当前请求range范围为:{}", range);
        }
        long size = file.length();
        BackupFileRangeVO full = BackupFileRangeVO.builder().start(0).end(size - 1).fullLength(size).total(size).build();
        BackupFileRangeVO downloadRange = getRange(range, size, full);
        return BackupDownloadEntity.builder()
                .file(file)
                .attachmentName(databaseFilename)
                .start(downloadRange.getStart())
                .end(downloadRange.getEnd())
                .fullLength(downloadRange.getFullLength())
                .total(downloadRange.getTotal())
                .build();
    }

    /** 厂商 `getRange:1204-1226`。 */
    private BackupFileRangeVO getRange(String range, Long size, BackupFileRangeVO full) {
        if (range == null) {
            return full;
        }
        if (!range.matches("^bytes=\\d*-\\d*$")) {
            throw new WrongArgumentException("fdfs.file.download.range.format.error", range);
        }
        String part = range.substring(6);
        int delimiterIndex = part.indexOf("-");
        long start = BackupFileRangeVO.subLong(part, 0, delimiterIndex);
        long end = BackupFileRangeVO.subLong(part, delimiterIndex + 1, part.length());
        if (start == -1L) {
            start = size - end;
            end = size - 1L;
        } else if (end == -1L || end > size - 1L) {
            end = size - 1L;
        }
        return BackupFileRangeVO.builder().start(start).end(end).fullLength(end - start + 1L).total(size).build();
    }

    /** 厂商 `getFileBackUp:1888-1890`。 */
    @Override
    public String backupDir() {
        return this.fileBackUp;
    }

    // ================================================================ 写

    /** 六个基础数据 key 的元信息（厂商 `backUpBaseInfo` / `downloadBaseInfo` 里逐段硬编码的那份表）。 */
    private record BaseDataItem(String key, String fileName, String backupErrorKey, String downloadErrorKey) {
    }

    private static final List<BaseDataItem> BASE_ITEMS = List.of(
            new BaseDataItem("user", "user.xls", "sync.backup.user.data.exception",
                    "sync.download.user.data.exception"),
            new BaseDataItem("role", "role.xls", "sync.backup.storage.data.exception",
                    "sync.download.storage.data.exception"),
            new BaseDataItem("sub_role", "sub_role.xls", "sync.backup.storage.data.exception",
                    "sync.download.storage.data.exception"),
            new BaseDataItem("software", "software.json", "sync.backup.software.data.exception",
                    "sync.download.software.data.exception"),
            new BaseDataItem("ingress", "ingress.json", "sync.backup.lb.data.exception",
                    "sync.download.lb.data.exception"),
            new BaseDataItem("configmap", "configmap.json", "sync.backup.config.group.data.exception",
                    "sync.download.config.group.data.exception"));

    /** 厂商 `backUpBaseInfo:271-381`。 */
    @Override
    public boolean backUpBaseInfo() throws IOException {
        String zipFullName = "baseBackupInfo.zip";
        File zipFile = new File(this.fileBackUp, zipFullName);
        File fileParent = zipFile.getParentFile();
        if (!fileParent.exists()) {
            fileParent.mkdirs();
        }
        if (!zipFile.exists() && !zipFile.createNewFile()) {
            throw new IOException("create baseBackupInfo.zip failed");
        }
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile));
        long updateTime = System.currentTimeMillis();
        for (BaseDataItem item : BASE_ITEMS) {
            InputStream inputStream = this.baseDataExportPort.openBaseData(item.key());
            try {
                File saveFile = saveFile(inputStream, item.fileName());
                inputStream.close();
                try (FileInputStream fileInputStream = new FileInputStream(saveFile)) {
                    fileAllToZip(fileInputStream, zipOut, item.fileName());
                }
                getBackupInfo(item.key(), updateTime, saveFile.length(), item.fileName());
            } catch (IOException e) {
                log.error("备份" + item.fileName() + "出现错误，异常为:{}", e);
                updateBackUpByError(item.key(), updateTime,
                        // 照抄厂商 :376：configmap 出错时传的是 **ingress** 的文件名
                        "configmap".equals(item.key()) ? "ingress.json" : item.fileName(),
                        "在进行备份" + item.fileName() + "时出现异常，请查看详细错误日志");
                throw new WrongArgumentException(item.backupErrorKey());
            }
        }
        zipOut.close();
        return true;
    }

    /** 厂商 `saveFile:237-246`。 */
    private File saveFile(InputStream in, String fileName) throws IOException {
        File filePath = new File(this.fileBackUp);
        if (!filePath.exists() && !filePath.mkdirs()) {
            throw new SystemException("global.save.file.failed");
        }
        File files = new File(filePath, fileName);
        Files.copy(in, files.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return files;
    }

    /** 厂商 `fileAllToZip:207-215`（**会关输入流**）。 */
    private void fileAllToZip(InputStream in, ZipOutputStream zipOut, String fileName) throws IOException {
        byte[] bufferArea = new byte[10240];
        zipOut.putNextEntry(new ZipEntry(fileName));
        int length;
        while ((length = in.read(bufferArea, 0, 10240)) != -1) {
            zipOut.write(bufferArea, 0, length);
        }
        in.close();
    }

    /** 厂商 `getBackupInfo:635-645`：`updateByPrimaryKey`（**整行**，msg 置 null）。 */
    private void getBackupInfo(String dataKey, long updateTime, long saveFile, String info) {
        this.backupRepository.queryByKey(dataKey).ifPresent(data -> {
            data.setMsg(null);
            data.setUpdatedAt(updateTime);
            data.setSize(saveFile);
            data.setDatabaseFilename(info);
            this.backupRepository.updateByPrimaryKey(data);
        });
    }

    /** 厂商 `updateBackUpByError:623-633`：`updateByPrimaryKeySelective`（size 置 0 + msg）。 */
    private void updateBackUpByError(String dataKey, long updateTime, String fileName, String msg) {
        this.backupRepository.queryByKey(dataKey).ifPresent(data -> {
            data.setUpdatedAt(updateTime);
            data.setSize(0L);
            data.setDatabaseFilename(fileName);
            data.setMsg(msg);
            this.backupRepository.updateByPrimaryKeySelective(data);
        });
    }

    /**
     * 厂商 `BackService.backupInfoToJob:383-406`。
     *
     * <p>**关键照抄点**：厂商 `scheduleJob` 的真实任务体是
     * {@code controller/Job/BackUpJob.execute}（触发层，见 {@link #backupJob()}），
     * 而 {@code BackService.backupInfo:449-621} 是**另一份**近乎重复的遗留实现
     * （它的第一句 `if (backupStatus.getStatus()==1) return true;` 会让它自己被
     * `backupInfoToJob` 预先置好的 status=1 直接短路 ⇒ 从 HTTP 入口**不可达**）。
     * 本重写照抄这个结构：`backupInfoToJob` 调度的是 {@code backupJob()}，
     * `backupInfo()` 保留为遗留实现。
     */
    @Override
    public boolean backupInfoToJob() {
        if (this.jobExists) {
            log.warn("Backup job already exists.");
            throw new WrongArgumentException("Backup job already exists.");
        }
        BackupStatusHolder.jobInterrupted = false;
        BackupStatusEntity status = BackupStatusEntity.init();
        status.setStatus(1);
        status.setCurrentType("gaop");
        status.setCurrent(0);
        status.setTotal(1);
        BackupStatusHolder.backupStatus = status;
        this.jobExists = true;
        this.jobExecutor.submit(() -> {
            try {
                backupJob();
            } catch (Exception e) {
                log.error("一键备份任务执行失败", e);
            } finally {
                this.jobExists = false;
            }
        });
        log.info("--------scheduler start ! ------------");
        return true;
    }

    /**
     * 厂商 {@code controller/Job/BackUpJob.execute}（原始行号：`recovered/gaop-be/.../controller/Job/BackUpJob.java`）
     * 的主流程复刻。
     *
     * <p>照抄点：
     * <ul>
     *   <li>`total = 7`（6 份框架数据 + database；镜像数量再加）</li>
     *   <li>先把 6 份框架数据写到 `<backup>/temp/` 下并压进 `<backup>/temp/baseBackupInfo.zip`，
     *       最后再把 temp 目录下的文件/子目录**逐个搬到备份根目录**（覆盖同名文件），
     *       然后删掉 temp 目录，最后才 `getBackupInfo(...)` 批量更新 `g_backup`</li>
     *   <li>每一步之间都有 `flag` 中断检查；中断时 status=3 并 `BackStatus.init()`</li>
     *   <li>configmap 出错时 `updateBackUpByError` 传的仍是 **ingress 的文件名**（照抄）</li>
     * </ul>
     * <p>未移植：镜像分支（Harbor）与 `getServiceInputStreamTest("database", ...)` 的
     * md5/中断细节之外的差异（database 分支复用 {@link #getServiceInputStream}）。
     */
    private boolean backupJob() {
        BackupStatusEntity status = BackupStatusHolder.backupStatus;
        if (status.getStatus() != 1) {
            status = BackupStatusEntity.init();
        }
        status.setStatus(1);
        status.setCurrentType("gaop");
        BackupLogHolder.sendBackupLog("gaop", "backup.start.message", null, "info");
        int total = 7;
        String databaseZipInfo = "dataBase_backup.zip";
        // 未移植：ImageService.getServiceRelationImage()（Harbor 域）⇒ 镜像项数量恒为 0
        log.info("总共需要备份的数量为:" + total);
        status.setTotal(total);
        status.setCurrent(0);
        BackupStatusHolder.backupStatus = status;
        if (interruptedJob()) {
            return false;
        }
        String zipFullName = "baseBackupInfo.zip";
        String tempPath = this.fileBackUp + File.separator + "temp";
        File tempFileInfo = new File(tempPath);
        if (tempFileInfo.exists() && !tempFileInfo.delete()) {
            log.warn("旧临时文件目录删除失败:{}", tempPath);
        }
        File zipFile = new File(tempPath, zipFullName);
        File fileParent = zipFile.getParentFile();
        if (!fileParent.exists()) {
            fileParent.mkdirs();
        }
        List<BaseDataItem> jobItems = BASE_ITEMS;
        long updateTime = System.currentTimeMillis();
        List<Object[]> backupInfoTempList = new ArrayList<>();
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (BaseDataItem item : jobItems) {
                status.setCurrentBackupName(item.fileName());
                status.setCurrent(status.getCurrent() + 1);
                log.info("当前备份的名称为:{},当前备份项进度为:{}/{}",
                        status.getCurrentBackupName(), status.getCurrent(), status.getTotal());
                InputStream inputStream = this.baseDataExportPort.openBaseData(item.key());
                try {
                    File saveFile = saveFileByTemp(inputStream, item.fileName(), tempPath);
                    inputStream.close();
                    try (FileInputStream fileInputStream = new FileInputStream(saveFile)) {
                        fileAllToZipNoClose(fileInputStream, zipOut, item.fileName());
                    }
                    backupInfoTempList.add(new Object[]{item.key(), updateTime, saveFile.length(), item.fileName()});
                } catch (IOException e) {
                    log.error("备份" + item.fileName() + "出现错误，异常为:{}", e);
                    status.setStatus(3);
                    updateBackUpByError(item.key(), updateTime,
                            "configmap".equals(item.key()) ? "ingress.json" : item.fileName(),
                            "在进行备份" + item.fileName() + "时出现异常，请查看详细错误日志");
                    throw new WrongArgumentException(item.backupErrorKey());
                }
                if (interruptedJob()) {
                    return false;
                }
            }
        } catch (IOException e) {
            throw new SystemException("global.save.file.failed");
        }
        status.setCurrentType("database");
        status.setCurrentBackupName(databaseZipInfo);
        status.setCurrent(status.getCurrent() + 1);
        log.info("当前备份的名称为:{},当前备份项进度为:{}/{}",
                status.getCurrentBackupName(), status.getCurrent(), status.getTotal());
        try {
            getServiceInputStream("database", updateTime);
            backupInfoTempList.add(new Object[]{"database", updateTime,
                    new File(this.fileBackUp, databaseZipInfo).length(), databaseZipInfo});
        } catch (Exception e) {
            log.error("获取业务数据库信息出现了异常，异常为:", e);
            status.setStatus(3);
            updateBackUpByError("database", updateTime, databaseZipInfo,
                    "在进行备份业务数据库数据时出现异常，请查看详细错误日志");
            throw new WrongArgumentException("sync.backup.business.db.exception");
        }
        if (interruptedJob()) {
            return false;
        }
        status.setCurrentType("image");
        if (interruptedJob()) {
            return false;
        }
        // 把 temp 目录下的文件搬到备份根目录（覆盖）
        File tempFile = new File(tempPath);
        File[] tempFiles = tempFile.listFiles();
        if (tempFiles != null) {
            for (File file : tempFiles) {
                File oldFile = new File(this.fileBackUp, file.getName());
                try {
                    if (oldFile.exists()) {
                        Files.delete(oldFile.toPath());
                    }
                    if (file.isDirectory()) {
                        copyDirectory(file.toPath(), oldFile.toPath());
                    } else {
                        Files.copy(file.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.error("复制文件异常:{}", e);
                }
            }
        }
        deleteRecursively(new File(tempPath));
        backupInfoTempList.forEach(r ->
                getBackupInfo((String) r[0], (Long) r[1], (Long) r[2], (String) r[3]));
        log.info("数据库信息更新完成");
        if (status.getCurrent() == status.getTotal()) {
            status.setStatus(2);
            log.info("备份完成，已推送备份完成信息");
        } else {
            status.setStatus(3);
        }
        BackupStatusHolder.backupStatus = BackupStatusEntity.init();
        return true;
    }

    /** 厂商 `BackUpJob.flag.booleanValue()` 的中断检查段。 */
    private boolean interruptedJob() {
        if (BackupStatusHolder.jobInterrupted) {
            log.info("当前标识状态:{}", BackupStatusHolder.jobInterrupted);
            BackupStatusHolder.backupStatus.setStatus(3);
            log.info("备份中断，已推送备份中断信息");
            BackupStatusHolder.backupStatus = BackupStatusEntity.init();
            return true;
        }
        return false;
    }

    /** 厂商 `BackService.saveFileByTemp:226-235`。 */
    private File saveFileByTemp(InputStream in, String fileName, String path) throws IOException {
        File filePath = new File(path);
        if (!filePath.exists() && !filePath.mkdirs()) {
            throw new SystemException("global.save.file.failed");
        }
        File files = new File(filePath, fileName);
        Files.copy(in, files.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return files;
    }

    /** 与 {@link #fileAllToZip} 相同但**不关闭输入流**（厂商 `saveFileByTemp` 路径用的是这一个）。 */
    private void fileAllToZipNoClose(InputStream in, ZipOutputStream zipOut, String fileName) throws IOException {
        byte[] bufferArea = new byte[10240];
        zipOut.putNextEntry(new ZipEntry(fileName));
        int length;
        while ((length = in.read(bufferArea, 0, 10240)) != -1) {
            zipOut.write(bufferArea, 0, length);
        }
    }

    /** 厂商 `FileUtils.copyDirectory` 的等价实现（infra/domain 无 commons-io）。 */
    private static void copyDirectory(java.nio.file.Path source, java.nio.file.Path target) throws IOException {
        Files.createDirectories(target);
        File[] children = source.toFile().listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            java.nio.file.Path dest = target.resolve(child.getName());
            if (child.isDirectory()) {
                copyDirectory(child.toPath(), dest);
            } else {
                Files.copy(child.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** 厂商 `BackService.deleteFile:248-269` 的等价实现（递归删目录）。 */
    private static void deleteRecursively(File file) {
        if (file.isFile()) {
            if (!file.delete()) {
                log.warn("文件删除失败:{}", file);
            }
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            log.warn("目录删除失败:{}", file);
        }
    }

    /**
     * 厂商 `BackService.backupInfo:449-621`（**遗留实现**）。
     *
     * <p>照抄点：第一句 `if (backupStatus.getStatus() == 1) return true;`——
     * 因为 `backupInfoToJob` 会先把 status 置 1，所以这个方法从 HTTP 入口**永远不会真正干活**
     * （属厂商的死代码/重复实现）。本重写如实保留这个短路。
     */
    @Override
    public boolean backupInfo() {
        BackupStatusEntity status = BackupStatusHolder.backupStatus;
        if (status.getStatus() == 1) {
            return true;
        }
        status = BackupStatusEntity.init();
        status.setStatus(1);
        status.setCurrentType("gaop");
        int total = BASE_TOTAL;
        String databaseZipInfo = "dataBase_backup.zip";
        boolean existDatabaseFileInfo = getExistDatabaseFileInfo(databaseZipInfo);
        if (existDatabaseFileInfo) {
            ++total;
        }
        log.info("开始获取所有服务关联的镜像数量信息");
        log.info("总共需要备份的数量为:" + total);
        status.setTotal(total);
        BackupStatusHolder.backupStatus = status;
        try {
            String zipFullName = "baseBackupInfo.zip";
            File zipFile = new File(this.fileBackUp, zipFullName);
            File fileParent = zipFile.getParentFile();
            if (!fileParent.exists()) {
                fileParent.mkdirs();
            }
            if (!zipFile.exists()) {
                zipFile.createNewFile();
            }
            long updateTime = System.currentTimeMillis();
            try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile))) {
                for (BaseDataItem item : BASE_ITEMS) {
                    if ("sub_role".equals(item.key())) {
                        continue;
                    }
                    status.setCurrentBackupName(item.fileName());
                    status.setCurrent(status.getCurrent() + 1);
                    log.info("当前备份的名称为:{},当前备份项进度为:{}/{}",
                            status.getCurrentBackupName(), status.getCurrent(), status.getTotal());
                    InputStream inputStream = this.baseDataExportPort.openBaseData(item.key());
                    try {
                        File saveFile = saveFile(inputStream, item.fileName());
                        inputStream.close();
                        try (FileInputStream fileInputStream = new FileInputStream(saveFile)) {
                            fileAllToZip(fileInputStream, zipOut, item.fileName());
                        }
                        getBackupInfo(item.key(), updateTime, saveFile.length(), item.fileName());
                    } catch (IOException e) {
                        log.error("备份" + item.fileName() + "出现错误，异常为:{}", e);
                        status.setStatus(3);
                        updateBackUpByError(item.key(), updateTime, item.fileName(),
                                "在进行备份" + item.fileName() + "时出现异常，请查看详细错误日志");
                        throw new WrongArgumentException(item.backupErrorKey());
                    }
                }
            }
            status.setCurrentType("database");
            if (existDatabaseFileInfo) {
                status.setCurrentBackupName(databaseZipInfo);
                status.setCurrent(status.getCurrent() + 1);
                getServiceInputStream("database", updateTime);
            } else {
                log.info("未获取到保存数据库的快照信息，一键备份跳过数据库的备份");
            }
            status.setCurrentType("image");
            if (status.getCurrent() == status.getTotal()) {
                status.setStatus(2);
            } else {
                status.setStatus(3);
            }
            return true;
        } catch (IOException e) {
            throw new SystemException("global.save.file.failed");
        } finally {
            BackupStatusHolder.backupStatus = BackupStatusEntity.init();
        }
    }


    /** 厂商 `cancelBackupJob:408-424`。 */
    @Override
    public boolean cancelBackupJob() {
        log.info("当前正在运行的任务：{}", getCurrentlyRunningJobs());
        BackupStatusHolder.jobInterrupted = true;
        this.jobExists = false;
        BackupStatusEntity status = BackupStatusHolder.backupStatus;
        status.setStatus(0);
        status.setTotal(0);
        status.setCurrent(0);
        log.info("备份定时器关闭");
        log.info("当前正在运行的任务：{}", getCurrentlyRunningJobs());
        return true;
    }

    /** 厂商 `getCurrentlyRunningJobs:426-447`：用 Executor 时只能报"是否在跑"。 */
    private List<Map<String, String>> getCurrentlyRunningJobs() {
        List<Map<String, String>> runningJobs = new ArrayList<>();
        if (this.jobExists) {
            HashMap<String, String> jobInfo = new HashMap<>();
            jobInfo.put("任务名称", "backupJob");
            jobInfo.put("任务组名", "group1");
            jobInfo.put("触发器名称", "backupTrigger");
            jobInfo.put("触发器组名", "triggerGroup1");
            jobInfo.put("当前状态", "RUNNING");
            // 厂商这里从 Quartz 的 Trigger 取；Executor 下如实留空串
            jobInfo.put("下一次触发时间", "null");
            jobInfo.put("上一次触发时间", "null");
            runningJobs.add(jobInfo);
        }
        return runningJobs;
    }


    /** 厂商 `DatabaseService.getExistDatabaseFileInfo:2079-2082`。 */
    private boolean getExistDatabaseFileInfo(String dataBaseFileName) {
        return this.databaseRepository.countFiles(this.cicdProjectId, dataBaseFileName) != 0;
    }

    /**
     * 厂商 `getServiceInputStream:891-1052`。
     *
     * <p>只移植 **database** 分支；**image** 分支（Harbor 拉 `*.tar.gz`）未移植，见类 javadoc。
     */
    private void getServiceInputStream(String dataKey, Long updateTime) throws IOException {
        if ("database".equals(dataKey)) {
            String databaseZipInfo = "dataBase_backup.zip";
            File databaseZip = File.createTempFile(databaseZipInfo, ".zip");
            this.databaseService.saveDataBaseSnapShot(databaseZip, this.fileBackUp, 1);
            File dbFile = new File(this.fileBackUp, databaseZipInfo);
            if (dbFile.exists()) {
                getBackupInfo(dataKey, updateTime, dbFile.length(), databaseZipInfo);
                File md5Dir = new File(this.fileBackUp + File.separator + "md5");
                if (!md5Dir.exists()) {
                    log.info("不存在获取md5目录下信息，进行创建md5目录");
                    md5Dir.mkdirs();
                }
                log.info("开始存储md5文件");
                File md5File = new File(md5Dir, "dataBase_backup.txt");
                if (md5File.exists() && !md5File.delete()) {
                    log.warn("旧 md5 文件删除失败");
                }
                try (FileOutputStream out = new FileOutputStream(md5File)) {
                    out.write(md5Hex(dbFile).getBytes());
                    out.flush();
                }
                log.info("存储md5信息完成");
            } else {
                log.info("未找到存在服务器上压缩的数据库包，跳过备份数据库与相关数据库用户信息");
            }
        }
        // image 分支未移植（Harbor 域）
    }

    /** 厂商 `DigestUtils.md5Hex(FileInputStream)` 的等价实现（infra 无 commons-codec）。 */
    private static String md5Hex(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                digest.update(buf, 0, len);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** 厂商 `singleBackupByBase:1066-1091`。 */
    @Override
    public boolean singleBackupByBase(String dataKey) throws IOException {
        log.info("将要备份的标识为:" + dataKey);
        BackupEntity data = this.backupRepository.queryByKey(dataKey).orElse(null);
        if (data == null) {
            throw new WrongArgumentException("backup.fill.data.identifier.not.found");
        }
        data.setMsg(null);
        String databaseFilename = data.getDatabaseFilename();
        File f = new File(this.fileBackUp, databaseFilename);
        if (f.exists() && !f.delete()) {
            log.warn("旧备份文件删除失败:{}", databaseFilename);
        } else if (!f.exists()) {
            log.info("旧备份文件不存在，无需删除");
        } else {
            log.info("移除旧的备份文件成功");
        }
        File file = saveFile(this.baseDataExportPort.openBaseData(dataKey), databaseFilename);
        if (file.exists()) {
            log.info("保存新备份数据成功");
            data.setUpdatedAt(System.currentTimeMillis());
            data.setSize(file.length());
            this.backupRepository.updateByPrimaryKey(data);
            return true;
        }
        log.info("保存新备份数据失败");
        return false;
    }

    /** 厂商 `singleBackupByService:1054-1064`。 */
    @Override
    public boolean singleBackupByService(String dataKey) throws IOException {
        long timeMillis = System.currentTimeMillis();
        log.info("将要备份服务数据的标识为:" + dataKey);
        if (this.backupRepository.queryByKey(dataKey).isEmpty()) {
            throw new WrongArgumentException("backup.invalid.backup.identifier");
        }
        getServiceInputStream(dataKey, timeMillis);
        return true;
    }

    /** 厂商 `resetAll:1711-1721`。 */
    @Override
    public boolean resetAll() {
        resetByKey("user");
        resetByKey("role");
        resetByKey("sub_role");
        resetByKey("software");
        resetByKey("ingress");
        resetByKey("configmap");
        resetByKey("image");
        resetByKey("database");
        return true;
    }

    /** 厂商 `resetByKey:1723-1766`（除 database 外的分支见类 javadoc 的"缺的一段"）。 */
    @Override
    public boolean resetByKey(String key) {
        boolean isUnknownKey = false;
        long start = System.currentTimeMillis();
        if ("user".equals(key)) {
            log.info("开始重置用户");
            log.warn("重置用户未移植（依赖用户域 resetUser），本次跳过");
        } else if ("role".equals(key)) {
            log.info("开始重置战位");
            log.warn("重置战位未移植（依赖战位域 resetRole），本次跳过");
        } else if ("sub_role".equals(key)) {
            log.info("开始重置子角色");
            log.warn("重置子角色未移植（依赖子角色域 resetSubRole），本次跳过");
        } else if ("software".equals(key)) {
            log.info("开始重置软件数据");
            log.warn("重置软件未移植（依赖软件域 resetSoftware + appService.resetService + whiteListService.resetAll），本次跳过");
        } else if ("configmap".equals(key)) {
            log.info("开始重置配置组");
            log.warn("重置配置组未移植（依赖 K8s ConfigMap 批量删除），本次跳过");
        } else if ("ingress".equals(key)) {
            log.info("开始重置负载均衡");
            log.warn("重置负载均衡未移植（依赖 K8s ConfigMap 的 tcp/udp-services 清空），本次跳过");
        } else if ("database".equals(key)) {
            log.info("开始重置数据库");
            this.databaseService.deleteDatabaseByReset();
            log.info("重置数据库用户");
            log.warn("重置数据库用户未移植（g_database_user 未建模），本次跳过");
        } else if ("image".equals(key)) {
            log.info("开始重置镜像");
            log.warn("重置镜像未移植（依赖 Harbor/镜像域），本次跳过");
        } else {
            isUnknownKey = true;
        }
        log.info("花费时间:{}毫秒", System.currentTimeMillis() - start);
        if (isUnknownKey) {
            throw new WrongArgumentException("reset.is.unknown.key");
        }
        return true;
    }

    /** 厂商 `downloadBaseInfo:1389-1489`。 */
    @Override
    public InputStream downloadBaseInfo() throws IOException {
        String baseBackUpFileName = "baseBackupInfo.zip";
        File file = new File(this.fileBackUp + File.separator + baseBackUpFileName);
        if (file.exists()) {
            log.info("存在备份的基础数据文件");
            return Files.newInputStream(file.toPath());
        }
        log.info("未获取到服务器上框架基本数据相关的备份文件,重新进行备份并下载");
        File zipFile = new File(this.fileBackUp, baseBackUpFileName);
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile))) {
            long updateTime = System.currentTimeMillis();
            for (BaseDataItem item : BASE_ITEMS) {
                // 照抄厂商 :1454：ingress 在下载路径里叫 **listenInfo.json**
                String fileName = "ingress".equals(item.key()) ? "listenInfo.json" : item.fileName();
                try {
                    InputStream inputStream = this.baseDataExportPort.openBaseData(item.key());
                    File saveFile = saveFile(inputStream, fileName);
                    inputStream.close();
                    try (FileInputStream fileInputStream = new FileInputStream(saveFile)) {
                        fileAllToZip(fileInputStream, zipOut, fileName);
                    }
                    getBackupInfo(item.key(), updateTime, saveFile.length(), fileName);
                } catch (Exception e) {
                    log.error("下载" + fileName + "出现错误，异常为:{}", e);
                    throw new WrongArgumentException(item.downloadErrorKey());
                }
            }
        }
        return Files.newInputStream(zipFile.toPath());
    }

    /**
     * 厂商 `downloadBackFile` 里真正写字节的那段（`RandomAccessFile` + `BufferedOutputStream`）。
     * 这里是 trigger 层写响应的辅助：从 `BackupDownloadEntity` 读出 [start, end] 区间的字节。
     *
     * <p>照抄厂商的循环结构（`transmitted + len <= fullLength` 的边界判断会**多发一个 4096 块**，
     * 再用 `transmitted < fullLength` 的补发兜住——保留这段古怪逻辑，见 `BackService.java:1365-1373`）。
     */
    public static void writeRange(BackupDownloadEntity entity, java.io.OutputStream rawOut) throws IOException {
        long fullLength = entity.getFullLength();
        long transmitted = 0L;
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(entity.getFile(), "r");
             java.io.BufferedOutputStream outputStream = new java.io.BufferedOutputStream(rawOut)) {
            byte[] buff = new byte[4096];
            int len = 0;
            randomAccessFile.seek(entity.getStart());
            while (transmitted + len <= fullLength && (len = randomAccessFile.read(buff)) != -1) {
                outputStream.write(buff, 0, len);
                transmitted += len;
            }
            if (transmitted < fullLength) {
                len = randomAccessFile.read(buff, 0, (int) (fullLength - transmitted));
                outputStream.write(buff, 0, len);
                transmitted += len;
            }
            outputStream.flush();
        }
    }
}
