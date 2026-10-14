package com.csm.paas.core.domain.software.valobj;

/**
 * `GET /v1/service/gaop` 的过滤规则（厂商 `ServiceController.getGaopServiceList:1563-1600`）。
 * <ol>
 *   <li>`appType` 标签存在：`type=1` → `gaop-core`；`type=2` → `gaop-base`；</li>
 *   <li>`appType` 标签不存在：`type=1` 与 `type=2` <b>都</b>按框架名单判断
 *       —— 厂商那两行是复制粘贴的同一句，`serviceInBaseService` 从未被调用。</li>
 * </ol>
 * 第 2 条是**刻意保留的缺陷照抄**：本环境没有 `system/appType` 标签，"修正"它会让 `type=2` 与厂商不同。
 */
public final class GaopServiceFilter {

    private GaopServiceFilter() {
    }

    public static boolean matches(Integer type, String name, String appType) {
        if (type == null || name == null) {
            return false;
        }
        if (appType != null) {
            if (type == 1) {
                return GaopServiceNames.APP_TYPE_GAOP_CORE.equals(appType);
            }
            if (type == 2) {
                return GaopServiceNames.APP_TYPE_GAOP_BASE.equals(appType);
            }
            return false;
        }
        return GaopServiceNames.serviceInPassFrame(name);
    }
}
