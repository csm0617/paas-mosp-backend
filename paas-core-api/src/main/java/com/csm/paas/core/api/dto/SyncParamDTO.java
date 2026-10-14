package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `com.glriverside.gaop.be.controller.request.sync.SyncParam`：
 * `POST /v1/sync` 的请求体 `{baseUrl, sync, data, userName}`（JSON 键名照抄厂商）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncParamDTO {

    /** 同步地址（对端 GAOP 节点）。 */
    private String baseUrl;

    /** `0` 增量、`1` 覆盖（原样传给每个 import 方法）。 */
    private int sync;

    /** 要同步的数据名列表。 */
    private List<String> data;

    /** 上传镜像时记录的用户名。 */
    private String userName;
}
