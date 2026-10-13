package com.csm.paas.core.types.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页归一化的厂商古怪语义（`recovered/gaop-be/.../utils/SqlUtils.java`）。
 * <p>这些断言看着"不合理"，但每一条都是厂商的实测行为，改掉就会与厂商分页不一致。
 */
class PageUtilsTest {

    @Test
    void pageDefaultsTo1OnlyWhenNullOrNegative() {
        assertEquals(1, PageUtils.normalizePage(null));
        assertEquals(1, PageUtils.normalizePage(-1));
        // 厂商 `page < 0` 才兜底，page=0 原样返回（PageHelper 会拿到 startRow=-size，是个真实坑）
        assertEquals(0, PageUtils.normalizePage(0));
        assertEquals(3, PageUtils.normalizePage(3));
    }

    @Test
    void sizeNullIs20ButNonPositiveIs100() {
        assertEquals(20, PageUtils.normalizeSize(null));
        assertEquals(100, PageUtils.normalizeSize(0), "厂商 size<=0 → MAX_SIZE_PER_PAGE=100，不是 20");
        assertEquals(100, PageUtils.normalizeSize(-5));
        assertEquals(15, PageUtils.normalizeSize(15));
        assertEquals(1000, PageUtils.normalizeSize(1000), "厂商不封顶（MAX_SIZE_PER_PAGE 只用于兜底分支）");
    }

    @Test
    void offsetMatchesPageHelperSemantics() {
        assertEquals(0, PageUtils.offset(1, 20));
        assertEquals(20, PageUtils.offset(2, 20));
        assertEquals(10, PageUtils.offset(3, 5));
    }
}
