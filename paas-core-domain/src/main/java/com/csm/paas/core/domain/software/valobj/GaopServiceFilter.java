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

    /**
     * 厂商过滤链的**最后一道合取项**（`ServiceController:1593-1594`）：
     * {@code boolean isGaopApp = labels.get("system/appName") != null && "gaop".equals(...);
     *       return isGaopApp && result;}
     * <p>即：**工作负载必须带标签 `system/appName=gaop`**，否则无论 type 是否匹配都被丢弃。
     * 这一条是 `gaop` 端点在参考环境返回空列表的**真正原因**（参考环境的 Deployment 只有 `app=` 标签）。
     */
    private static boolean isGaopApp(String appName) {
        return appName != null && "gaop".equals(appName);
    }

    public static boolean matches(Integer type, String name, String appType, String appName) {
        if (type == null || name == null) {
            return false;
        }
        if (appType != null) {
            if (type == 1) {
                return GaopServiceNames.APP_TYPE_GAOP_CORE.equals(appType) && isGaopApp(appName);
            }
            if (type == 2) {
                return GaopServiceNames.APP_TYPE_GAOP_BASE.equals(appType) && isGaopApp(appName);
            }
            return false;
        }
        return GaopServiceNames.serviceInPassFrame(name) && isGaopApp(appName);
    }
}
