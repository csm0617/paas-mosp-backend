package com.csm.paas.core.domain.software.service;

import com.csm.paas.core.domain.software.model.entity.SoftwareListEntity;

/** 软件目录查询服务（K2：`GET /v1/software`）。 */
public interface ISoftwareQueryService {

    /**
     * 软件列表（厂商 `GET /v1/software?page&size&name&type&order&sort`）。
     *
     * @param host 用于拼 CS 类型软件的 `gaop://cs?...` 入口地址（取自请求头 `x-forwarded-host`）
     */
    SoftwareListEntity querySoftwareList(int page, int size, String name, String type,
                                        String order, String sort, String host);

    /**
     * 软件授权列表（厂商 `GET /v1/software/auth` → `SoftwareService.getSoftwareAuthList:3280-3323`）。
     *
     * <p>每个软件的 `status` 三态判定（照抄）：
     * <ul>
     *   <li>`active_code` 空白 → **0**（未授权），且 `authCreateTime`/`authExpireTime` 都置 **null**；</li>
     *   <li>否则：`activeExpire != null && now > activeExpire` → **2**（已过期）；
     *       `activeExpire == null || == 0` → **0**；其余 → **1**（已授权）；</li>
     * </ul>
     * 注意 `active_code` 非空时两个时间字段**原样取**（不过滤 null）。
     * <p>排序**只按 `created_at DESC`**（与列表端点的 `order_top` 恒在最前不同）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthInfoVO> queryAuthList(
            int page, int size, String name, String type);

    /** 授权列表总数（同一套 WHERE）。 */
    long countAuthList(String name, String type);

    /**
     * 软件在某服务下的端口映射（厂商 `GET /v1/software/{softwareId}/service/{serviceName}`
     * → `SoftwareService.servicePortList:2304-2306`）：`software_id` + `service_name` 等值查询，**不排序**。
     * <p>找不到就是**空数组**（不报 404/400）——照抄。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePortVO> queryServicePorts(
            String softwareId, String serviceName);
}
