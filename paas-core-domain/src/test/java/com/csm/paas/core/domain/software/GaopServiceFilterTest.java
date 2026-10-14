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
        // 有标签时按标签判，完全不看名字
        assertTrue(GaopServiceFilter.matches(1, "任意服务名", "gaop-core"));
        assertFalse(GaopServiceFilter.matches(1, "任意服务名", "gaop-base"));
        assertTrue(GaopServiceFilter.matches(2, "任意服务名", "gaop-base"));
        assertFalse(GaopServiceFilter.matches(2, "任意服务名", "gaop-core"));
        // business 两个 type 都不匹配
        assertFalse(GaopServiceFilter.matches(1, "任意服务名", "business"));
        assertFalse(GaopServiceFilter.matches(2, "任意服务名", "business"));
    }

    @Test
    void withoutAppTypeLabelBothTypesUseTheFrameworkList() {
        assertTrue(GaopServiceFilter.matches(1, "gaop-be", null));
        assertTrue(GaopServiceFilter.matches(2, "gaop-be", null), "type=2 也走框架名单（照抄厂商缺陷）");
        assertFalse(GaopServiceFilter.matches(1, "gaop-fdfs", null), "gaop-fdfs 只在基础名单里");
    }

    /** 这一条就是那个缺陷的可执行证据：**基础名单里的服务，在 type=2 下查不出来**。 */
    @Test
    void baseOnlyServicesAreNeverReturnedWhenLabelIsAbsent() {
        for (String name : GaopServiceNames.BASE) {
            if (GaopServiceNames.PASS_FRAME.contains(name)) {
                continue;
            }
            assertFalse(GaopServiceFilter.matches(2, name, null),
                    name + " 在基础名单里，但因缺陷走的是框架名单 ⇒ 查不出来");
        }
        assertTrue(GaopServiceNames.serviceInBaseService("gaop-fdfs"), "名单本身没问题，是调用点用错了");
    }

    @Test
    void nullAppTypeAndNullTypeAreSafe() {
        assertFalse(GaopServiceFilter.matches(null, "gaop-be", null));
        assertFalse(GaopServiceFilter.matches(1, null, null));
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
