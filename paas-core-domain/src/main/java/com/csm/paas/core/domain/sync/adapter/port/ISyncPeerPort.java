package com.csm.paas.core.domain.sync.adapter.port;

import com.csm.paas.core.domain.sync.model.valobj.SyncFetchResultVO;

/**
 * 对端 GAOP 节点的 HTTP 拉取端口（厂商 `SyncController` 里的 `RestTemplate.exchange(...)`）。
 *
 * <p><b>类 C：本环境不可验证</b>——没有第二个 GAOP 节点可对拉。
 *
 * <p>JSON 解析在实现里（infrastructure），域层只拿到 `data` 节点的原样对象。
 */
public interface ISyncPeerPort {

    /**
     * `GET <url>`，取信封 `{msg,code,data}` 里的 `data` 节点。
     * <p>失败（连接异常 / 非 2xx / 解析失败）⇒ `ok=false`。
     */
    SyncFetchResultVO getData(String url);

    /**
     * `POST <url>` 下载二进制（数据库快照 zip），Accept 头照抄厂商
     * （`APPLICATION_OCTET_STREAM, *​/*`）。
     * <p>失败 ⇒ 抛运行时异常（域层 catch 后抛 `sync.request.sync.db.failed`）。
     */
    byte[] downloadPost(String url);

    /**
     * `GET <url>` 并读 `data` 的布尔值（厂商 `GET /v1/sync/database/exist` 里
     * `HttpRequest.get(finalUrl).timeout(10000)` + `jsonObject.get("data")`）。
     * <p>**任何异常 / 非 2xx / data 不是布尔 ⇒ false**（厂商 catch 后 `return Response.ok(false)`）。
     */
    boolean getBooleanData(String url);
}
