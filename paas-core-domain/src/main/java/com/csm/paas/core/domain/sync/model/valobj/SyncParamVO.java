package com.csm.paas.core.domain.sync.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `POST /v1/sync` 的入参（厂商 `controller/request/sync/SyncParam.java`）：
 * `{baseUrl, sync, data, userName}`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncParamVO {

    /** 同步地址（对端 GAOP 节点）。 */
    private String baseUrl;

    /** `0` 增量同步、`1` 覆盖同步（厂商 `SyncParam.sync`，原样传给每个 import 方法）。 */
    private int sync;

    /** 要同步的数据名列表（`user`/`role`/`sub_role`/`software`/`service`/`client`/`mirror`/`configmap`/`network`/`database`）。 */
    private List<String> data;

    /** 上传镜像时记录的用户名。 */
    private String userName;
}
