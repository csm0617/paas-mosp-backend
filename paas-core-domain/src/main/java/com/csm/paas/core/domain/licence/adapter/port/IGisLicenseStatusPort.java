package com.csm.paas.core.domain.licence.adapter.port;

/**
 * 请求 GIS 的授权状态接口（厂商 `getGisActiveStatus:1183-1199`：
 * `GET <gis.url>/earthview/license/getLicenseStatus` → 取响应里的 `EVServer`）。
 *
 * <p>调用失败 ⇒ 抛异常（调用方据此返回 2 = "无法请求到授权状态接口"）。
 */
public interface IGisLicenseStatusPort {

    /** @return GIS 返回的 `EVServer`（0/1） */
    Integer queryEvServer(String baseUrl);
}
