package com.csm.paas.core.domain.sync.service.impl;

import com.csm.paas.core.domain.software.model.valobj.ListenImportVO;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.domain.sync.adapter.port.ISyncPeerPort;
import com.csm.paas.core.domain.sync.adapter.port.ISyncProgressNotifyPort;
import com.csm.paas.core.domain.sync.model.SyncStatusHolder;
import com.csm.paas.core.domain.sync.model.entity.SyncPayloadEntity;
import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;
import com.csm.paas.core.domain.sync.model.valobj.SyncFetchResultVO;
import com.csm.paas.core.domain.sync.model.valobj.SyncParamVO;
import com.csm.paas.core.domain.sync.service.ISyncCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/**
 * `POST /v1/sync` 的实现（规格：厂商 `SyncController.syncData:258-467`）。
 *
 * <p><b>本轮实现的边界（重要，报告里逐条列出）</b>：
 * <ul>
 *   <li><b>完整实现</b>：状态机（含"同步中直接返回"、成功/失败后的**复位**怪点）、baseUrl 归一化、
 *       类型 flags、`total` 计算（含未拉用户时的 NPE 照抄）、后台裸线程、`sendStopSyncInfo` 语义、
 *       以及**对端拉取**（真 HTTP，`SyncPeerPort`，类 C）；</li>
 *   <li><b>导入侧只落地两处</b>：`configmap` 复用
 *       {@link IServiceQueryService#importConfigMaps}（存在 ⇒ replace、否则 create），
 *       `network` 复用 {@link IServiceQueryService#importListenConfig}（改名 + replace 两个
 *       `tcp-services-*`/`udp-services-*` ConfigMap）；</li>
 *   <li><b>未实现（显式记日志、不静默）</b>：`user`/`role`/`sub_role`/`software`/`client`/`mirror`/
 *       `service`/`database` 的导入——厂商这些路径分别依赖 `UserService.importUser`、
 *       `RoleService.importRole`、`SoftwareService.importSoftware`、SSH 拉 CS 包、Harbor 镜像、
 *       K8s Deployment 现场、mysqldump/SSH；本平台没有对应的"整包导入"入口
 *       （既有 import 端点收的是 Excel，不是对端 JSON）。</li>
 * </ul>
 *
 * <p><b>类 C：本环境不可验证</b>——整条链路都需要第二个 GAOP 节点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncCommandServiceImpl implements ISyncCommandService {

    /** 厂商 `SyncController` 顶部常量（`:141-150`）。 */
    private static final String SYNC_USER_KEY = "user";
    private static final String SYNC_ROLE_KEY = "role";
    private static final String SYNC_SUB_ROLE_KEY = "sub_role";
    private static final String SYNC_SOFTWARE_KEY = "software";
    private static final String SYNC_SERVICE_KEY = "service";
    private static final String SYNC_CLIENT_KEY = "client";
    private static final String SYNC_MIRROR_KEY = "mirror";
    private static final String SYNC_CONFIGMAP_KEY = "configmap";
    private static final String SYNC_NETWORK_KEY = "network";
    private static final String SYNC_DATABASE_KEY = "database";

    private final ISyncPeerPort syncPeerPort;
    private final ISyncProgressNotifyPort syncProgressNotifyPort;
    private final IServiceQueryService serviceQueryService;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    private static SyncStatusEntity status() {
        return SyncStatusHolder.INSTANCE;
    }

    @Override
    public boolean databaseExistsOnPeer(String url) {
        // 厂商 :1343-1346：text = "/api/gaop/"；finalUrl = "http://" + url + text + "v1/sync/database/status"
        return syncPeerPort.getBooleanData("http://" + url + "/api/gaop/v1/sync/database/status");
    }

    @Override
    public boolean startSync(SyncParamVO param) {
        SyncStatusEntity syncStatus = status();
        if (syncStatus.isSyncing()) {
            // 厂商 :260-263：同步中 ⇒ 重发一条进度后直接返回 true（不重复启动）
            syncProgressNotifyPort.syncProgress(syncStatus);
            return true;
        }
        String imageUrl = param.getBaseUrl();
        String finalUrl = param.getBaseUrl();
        boolean isHttp = finalUrl.matches("^http[s]?://(.*)");
        if (!isHttp) {
            finalUrl = "http://" + finalUrl + "/api/gaop/";
            imageUrl = "http://" + imageUrl;
        }
        if (!finalUrl.endsWith("/")) {
            finalUrl = finalUrl + "/";
        }
        int method = param.getSync();
        Set<String> names = new HashSet<>(param.getData());
        boolean isUser = names.contains(SYNC_USER_KEY);
        boolean isRole = names.contains(SYNC_ROLE_KEY);
        boolean isSubRole = names.contains(SYNC_SUB_ROLE_KEY);
        boolean isSoftware = names.contains(SYNC_SOFTWARE_KEY);
        boolean isService = names.contains(SYNC_SERVICE_KEY);
        boolean isImage = names.contains(SYNC_MIRROR_KEY);
        boolean isClient = names.contains(SYNC_CLIENT_KEY);
        boolean isConfigMap = names.contains(SYNC_CONFIGMAP_KEY);
        boolean isNetWork = names.contains(SYNC_NETWORK_KEY);
        boolean isDatabase = names.contains(SYNC_DATABASE_KEY);

        SyncPayloadEntity data = new SyncPayloadEntity();
        syncStatus.setSyncing(true);
        syncStatus.setSuccess(false);
        syncStatus.setCurrent(0);
        if (isUser) {
            data.setUserSync(asMap(getData(finalUrl + "v1/sync/user", "sync.request.sync.user.failed")));
        }
        if (isRole) {
            data.setRole(asList(getData(finalUrl + "v1/sync/role", "sync.request.sync.storage.failed")));
        }
        if (isSubRole) {
            data.setSubRole(asList(getData(finalUrl + "v1/sync/subRole", "sync.request.sync.storage.failed")));
        }
        if (isSoftware) {
            data.setSoftware(asList(getData(finalUrl + "v1/sync/software", "sync.request.sync.software.failed")));
            data.setSoftwareRelationService(asList(getData(
                    finalUrl + "/v1/sync/relationServiceWebEntry", "sync.request.sync.software.service.info.failed")));
        }
        if (isClient) {
            data.setClient(asList(getData(finalUrl + "v1/sync/client", "sync.request.sync.cs.client.failed")));
        }
        if (isImage) {
            data.setImageInfo(asList(getData(finalUrl + "/v1/sync/relationImage", "sync.request.sync.image.failed")));
        }
        if (isService) {
            data.setService(asList(getData(finalUrl + "v1/sync/service", "sync.request.sync.service.failed")));
            data.setNodePortService(asList(getData(finalUrl + "v1/sync/nodePortService", "sync.request.sync.service.failed")));
        }
        if (isConfigMap) {
            data.setConfigMap(asList(getData(finalUrl + "v1/sync/configMap", "sync.request.sync.configmap.failed")));
        }
        if (isNetWork) {
            data.setNetWorkInfo(asMap(getData(finalUrl + "v1/sync/syncTcpAndUdp", "sync.request.sync.lb.failed")));
        }
        if (isDatabase) {
            data.setDatabaseUser(asList(getData(finalUrl + "v1/sync/databaseUser", "sync.request.db.user.failed")));
            byte[] databaseBytes;
            try {
                databaseBytes = syncPeerPort.downloadPost(finalUrl + "/v1/sync/database/backup");
            } catch (Exception e) {
                log.error("请求同步数据库失败，同步地址错误", e);
                syncStatus.setSuccess(false);
                syncStatus.setSyncing(false);
                throw new WrongArgumentException("sync.request.sync.db.failed");
            }
            data.setDatabase(databaseBytes);
            data.setDatabaseSize(zipEntryCount(databaseBytes) - 2);
            data.setRelationDataBase(asList(getData(
                    finalUrl + "/v1/sync/relationDataBaseList", "sync.request.db.association.list.failed")));
        }
        int netWorkNumber = data.getNetWorkInfo() != null ? 2 : 0;
        // 厂商 :348 —— `data.getUserSync()` 未拉取时为 null ⇒ 这里 NPE（照抄，不修）
        syncStatus.setTotal(userListSize(data.getUserSync())
                + data.getRole().size() + data.getSubRole().size() + data.getSoftware().size()
                + data.getClient().size() + data.getImageInfo().size() + data.getService().size()
                + data.getConfigMap().size() + netWorkNumber + data.getDatabaseSize());
        log.debug("同步的总数:{}", syncStatus.getTotal());

        // 注意：`finalUrl` 在方法前半段被重新赋值过 ⇒ 不是 effectively-final，lambda 里只能捕获 `url`
        // （`url` 就是当时的 finalUrl，语义不变）
        String url = finalUrl;
        String finalImageUrl = imageUrl;
        Thread thread = new Thread(() -> runImportPhase(url, url, finalImageUrl, method, data,
                isUser, isRole, isSubRole, isSoftware, isService, isClient, isImage,
                isConfigMap, isNetWork, isDatabase));
        thread.setUncaughtExceptionHandler((t, e) -> log.error("同步异常", e));
        thread.start();
        return true;
    }

    /**
     * 厂商 `syncData` 里那个裸线程的主体（`:356-463`）：逐类型导入（各自 try/catch ⇒ `sendStopSyncInfo`）
     * → 组合同步 → 结束状态。
     */
    private void runImportPhase(String finalUrl, String url, String imageUrl, int method, SyncPayloadEntity data,
                                boolean isUser, boolean isRole, boolean isSubRole, boolean isSoftware,
                                boolean isService, boolean isClient, boolean isImage,
                                boolean isConfigMap, boolean isNetWork, boolean isDatabase) {
        SyncStatusEntity syncStatus = status();
        if (isUser) {
            try {
                importUser(data);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步用户出现错误");
            }
        }
        if (isRole) {
            try {
                importRole(data);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步战位出现错误");
            }
        }
        if (isSubRole) {
            try {
                importSubRole(data);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步子角色出现错误");
            }
        }
        if (isSoftware) {
            try {
                importSoftware(data);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步软件出现错误");
            }
        }
        if (isClient) {
            try {
                importClient(data);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步cs客户端包出现错误");
            }
        }
        if (isImage) {
            try {
                importImageInfo(data);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步镜像出现错误");
            }
        }
        if (isService) {
            try {
                importService(data);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步服务出现错误");
            }
        }
        if (isConfigMap) {
            try {
                // 复用既有配置组导入（存在 ⇒ replace、否则 create）
                serviceQueryService.importConfigMaps(namespace, data.getConfigMap());
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步配置组出现错误");
            }
        }
        if (isNetWork) {
            try {
                // 复用既有监听导入（改名 + replace 两个 ingress-lb ConfigMap）
                ListenImportVO listenImport = new ListenImportVO();
                if (data.getNetWorkInfo() != null) {
                    listenImport.setTcpListen(asMap(data.getNetWorkInfo().get("tcpListen")));
                    listenImport.setUdpListen(asMap(data.getNetWorkInfo().get("udpListen")));
                }
                serviceQueryService.importListenConfig(namespace, listenImport);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步网络负载均衡出现错误");
            }
        }
        if (isDatabase) {
            try {
                importDatabase(data);
            } catch (Exception e) {
                sendStopSyncInfo(e, "同步数据库出现错误");
            }
        }
        // 厂商 :429：无论 isSoftware 与否都调一次 importSoftwareRelationService
        importSoftwareRelationService(data);
        // 厂商 :446：concatSync（组合同步，走 SSH + 对端 HTTP，本轮未实现）
        log.warn("[sync] concatSync（组合同步）本轮未实现：isUser={}, isRole={}, isSoftware={}, "
                        + "isService={}, isClient={}, url={}",
                isUser, isRole, isSoftware, isService, isClient, finalUrl);
        syncStatus.setSuccess(true);
        syncStatus.setSyncing(false);
        syncStatus.setErrorStatus(null);
        syncStatus.setCurrent(syncStatus.getTotal());
        syncProgressNotifyPort.syncProgress(syncStatus);
        // 厂商 :459-462：推完**立刻复位**（所以 GET /status 事后看到的是初始态）
        syncStatus.setSuccess(false);
        syncStatus.setSyncing(false);
        syncStatus.setErrorStatus(null);
        syncStatus.setCurrent(0);
    }

    /**
     * 厂商 `SyncController.sendStopSyncInfo:469-481`：置 `errorStatus="fail"` → 推一次 →
     * **复位** → 抛 `WrongArgumentException`（在后台线程里只会终止该线程，被 uncaughtExceptionHandler 记日志）。
     */
    private void sendStopSyncInfo(Exception e, String info) {
        log.debug(info, e);
        SyncStatusEntity syncStatus = status();
        syncStatus.setSuccess(false);
        syncStatus.setSyncing(false);
        syncStatus.setErrorStatus("fail");
        syncStatus.setCurrent(0);
        syncProgressNotifyPort.syncProgress(syncStatus);
        syncStatus.setSuccess(false);
        syncStatus.setSyncing(false);
        syncStatus.setErrorStatus(null);
        syncStatus.setCurrent(0);
        throw new WrongArgumentException(info);
    }

    /** 厂商各 `getBeforeSync*` 的公共部分：失败 ⇒ 置状态后抛该类型自己的 i18n 键。 */
    private Object getData(String url, String failKey) {
        SyncFetchResultVO result = syncPeerPort.getData(url);
        if (!result.isOk()) {
            SyncStatusEntity syncStatus = status();
            syncStatus.setSuccess(false);
            syncStatus.setSyncing(false);
            throw new WrongArgumentException(failKey);
        }
        return result.getData();
    }

    // ---------- 导入侧：本轮落地/未实现的分界 ----------

    private void importUser(SyncPayloadEntity data) {
        log.warn("[sync] 用户导入未实现（厂商 UserService.importUser，本轮范围外）: 条数={}",
                data.getUserSync() == null ? 0 : userListSize(data.getUserSync()));
    }

    private void importRole(SyncPayloadEntity data) {
        log.warn("[sync] 战位导入未实现（厂商 RoleService.importRole，本轮范围外）: 条数={}", data.getRole().size());
    }

    private void importSubRole(SyncPayloadEntity data) {
        log.warn("[sync] 子角色导入未实现（厂商 SubRoleService.importSubRole，本轮范围外）: 条数={}", data.getSubRole().size());
    }

    private void importSoftware(SyncPayloadEntity data) {
        log.warn("[sync] 软件导入未实现（厂商 SoftwareService.importSoftware，本轮范围外）: 条数={}", data.getSoftware().size());
    }

    private void importClient(SyncPayloadEntity data) {
        log.warn("[sync] CS 客户端包导入未实现（厂商 CsSoftwareUploadService.importClient + SSH，本轮范围外）: 条数={}",
                data.getClient().size());
    }

    private void importImageInfo(SyncPayloadEntity data) {
        log.warn("[sync] 镜像导入未实现（厂商 importImageInfo + Harbor，本轮范围外）: 条数={}", data.getImageInfo().size());
    }

    private void importService(SyncPayloadEntity data) {
        log.warn("[sync] 服务导入未实现（厂商 importService 写 K8s Deployment，本轮范围外）: 条数={}", data.getService().size());
    }

    private void importDatabase(SyncPayloadEntity data) {
        log.warn("[sync] 数据库导入未实现（厂商 databaseService.importSqlBySync + mysqldump，本轮范围外）: 条数={}",
                data.getDatabaseUser().size());
    }

    private void importSoftwareRelationService(SyncPayloadEntity data) {
        if (data.getSoftwareRelationService().isEmpty()) {
            return;
        }
        log.warn("[sync] 软件-服务关联导入未实现（厂商 softwareService.importSoftwareRelationService，本轮范围外）: 条数={}",
                data.getSoftwareRelationService().size());
    }

    // ---------- 小工具 ----------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        return value == null ? new ArrayList<>() : (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value == null ? null : (Map<String, Object>) value;
    }

    /** 厂商 `data.getUserSync().getUserList().size()`：`userSync` 为 null 时 NPE（照抄）。 */
    @SuppressWarnings("unchecked")
    private static int userListSize(Map<String, Object> userSync) {
        return ((List<Map<String, Object>>) userSync.get("userList")).size();
    }

    /** 等价厂商 `new ZipFile(file).size()`（只数条目，不需要落临时文件）。 */
    private static int zipEntryCount(byte[] bytes) {
        if (bytes == null) {
            return 0;
        }
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zis.getNextEntry() != null) {
                count++;
            }
        } catch (Exception e) {
            log.error("读取数据库快照 zip 失败", e);
        }
        return count;
    }
}
