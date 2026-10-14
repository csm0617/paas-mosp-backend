package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.valobj.GaopServiceFilter;
import com.csm.paas.core.domain.software.valobj.GaopServiceNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `GET /v1/service/gaop` 的过滤规则（离线）。
 * <p>规则与缺陷出处：厂商 `ServiceController.getGaopServiceList:1563-1600`，见 `docs/findings/53`。
 */
class GaopServiceFilterTest {

    @Test
    void appTypeLabelWinsOverNameList() {
        // 有 appType 标签时按标签判，不看名字；但**仍要求 system/appName=gaop**
        assertTrue(GaopServiceFilter.matches(1, "任意服务名", "gaop-core", "gaop"));
        assertFalse(GaopServiceFilter.matches(1, "任意服务名", "gaop-base", "gaop"));
        assertTrue(GaopServiceFilter.matches(2, "任意服务名", "gaop-base", "gaop"));
        assertFalse(GaopServiceFilter.matches(2, "任意服务名", "gaop-core", "gaop"));
        // business 两个 type 都不匹配
        assertFalse(GaopServiceFilter.matches(1, "任意服务名", "business", "gaop"));
        assertFalse(GaopServiceFilter.matches(2, "任意服务名", "business", "gaop"));
    }

    /**
     * **这就是 `gaop` 端点在参考环境恒为空列表的真正原因**（`ServiceController:1593-1594`）：
     * 过滤链最后还有一道 `isGaopApp = labels["system/appName"] == "gaop"` 的合取项。
     * 参考环境的 Deployment 只有 `app=` 标签 ⇒ 无论 type 怎么选，**一条都不会留下**。
     */
    @Test
    void requiresAppNameLabelToBeGaop() {
        assertTrue(GaopServiceFilter.matches(1, "gaop-be", null, "gaop"), "有 appName=gaop 才可能留下");
        assertFalse(GaopServiceFilter.matches(1, "gaop-be", null, null), "没有 system/appName 标签 ⇒ 丢弃");
        assertFalse(GaopServiceFilter.matches(1, "gaop-be", null, "其他"), "appName 不是 gaop ⇒ 丢弃");
        assertFalse(GaopServiceFilter.matches(1, "gaop-be", "gaop-core", null), "有 appType 也照样要过 appName 这一关");
    }

    @Test
    void withoutAppTypeLabelBothTypesUseTheFrameworkList() {
        assertTrue(GaopServiceFilter.matches(1, "gaop-be", null, "gaop"));
        assertTrue(GaopServiceFilter.matches(2, "gaop-be", null, "gaop"), "type=2 也走框架名单（照抄厂商缺陷）");
        assertFalse(GaopServiceFilter.matches(1, "gaop-fdfs", null, "gaop"), "gaop-fdfs 只在基础名单里");
    }

    /** 这一条就是那个缺陷的可执行证据：**基础名单里的服务，在 type=2 下查不出来**。 */
    @Test
    void baseOnlyServicesAreNeverReturnedWhenLabelIsAbsent() {
        for (String name : GaopServiceNames.BASE) {
            if (GaopServiceNames.PASS_FRAME.contains(name)) {
                continue;
            }
            assertFalse(GaopServiceFilter.matches(2, name, null, "gaop"),
                    name + " 在基础名单里，但因缺陷走的是框架名单 ⇒ 查不出来");
        }
        assertTrue(GaopServiceNames.serviceInBaseService("gaop-fdfs"), "名单本身没问题，是调用点用错了");
    }

    @Test
    void nullAppTypeAndNullTypeAreSafe() {
        assertFalse(GaopServiceFilter.matches(null, "gaop-be", null, "gaop"));
        assertFalse(GaopServiceFilter.matches(1, null, null, "gaop"));
    }

    @Test
    void displayNamesMatchVendorTable() {
        assertEquals(18, GaopServiceNames.DISPLAY_NAMES.size());
        assertEquals("框架后端", GaopServiceNames.fixedName("gaop-be"));
        assertEquals("网关", GaopServiceNames.fixedName("gaop-gateway-auth"));
        assertEquals("业务数据库", GaopServiceNames.fixedName("gaop-radondb-mysql-leader"));
        assertEquals("业务数据库", GaopServiceNames.fixedName("mysql"));
        assertEquals("未登记的名字", GaopServiceNames.fixedName("未登记的名字"), "取不到就回退原名");
        assertEquals(9, GaopServiceNames.PASS_FRAME.size());
        assertEquals(7, GaopServiceNames.BASE.size());
    }
}
