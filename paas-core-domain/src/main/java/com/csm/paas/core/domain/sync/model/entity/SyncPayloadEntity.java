package com.csm.paas.core.domain.sync.model.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * `POST /v1/sync` 从对端拉回来的数据（厂商 `controller/response/sync/SyncResult.java`）。
 *
 * <p>厂商那些 `List` 字段都**初始化为空列表**（只有 `userSync`/`nodePortService`/`netWorkInfo`/`database`
 * 默认 null）——这直接决定了 `syncStatus.setTotal(...)` 里哪些字段会 NPE。这里照抄同样的默认值。
 *
 * <p>元素用原样的 `Map`（JSON 解析在 infrastructure 的 `SyncPeerPort`，域层不碰 Jackson）。
 */
@Data
public class SyncPayloadEntity {

    /** 厂商 `userSync`：默认 **null**；`total` 里 `getUserSync().getUserList()` 会因此 NPE（照抄）。 */
    private Map<String, Object> userSync;

    private List<Map<String, Object>> role = new ArrayList<>();
    private List<Map<String, Object>> subRole = new ArrayList<>();
    private List<Map<String, Object>> software = new ArrayList<>();
    private List<Map<String, Object>> service = new ArrayList<>();
    private List<Map<String, Object>> image = new ArrayList<>();
    private List<Map<String, Object>> client = new ArrayList<>();
    private List<Map<String, Object>> configMap = new ArrayList<>();
    private List<Map<String, Object>> nodePortService;
    private Map<String, Object> netWorkInfo;
    private List<Map<String, Object>> imageInfo = new ArrayList<>();

    // 以下为 sync 流程自己用到的中间量（厂商是局部变量，这里收进载荷方便跨方法传递）
    private List<Map<String, Object>> softwareRelationService = new ArrayList<>();
    private List<Map<String, Object>> databaseUser = new ArrayList<>();
    private List<Map<String, Object>> relationDataBase = new ArrayList<>();
    private byte[] database;
    private int databaseSize;
}
