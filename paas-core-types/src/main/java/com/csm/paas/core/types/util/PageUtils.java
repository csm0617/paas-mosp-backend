package com.csm.paas.core.types.util;

/**
 * 分页参数归一化——**逐字照抄厂商** {@code com.glriverside.gaop.be.utils.SqlUtils}
 * （{@code recovered/gaop-be/.../utils/SqlUtils.java}）。
 * <p>厂商的三条古怪语义必须保留，否则分页行为会与厂商不同：
 * <ul>
 *   <li>{@code page == null || page < 0} → 1（注意 {@code page == 0} **原样返回 0**）</li>
 *   <li>{@code size == null} → 20</li>
 *   <li>{@code size <= 0} → **100**（不是 20；厂商常量 {@code MAX_SIZE_PER_PAGE=100} 只在这里用到）</li>
 * </ul>
 */
public final class PageUtils {

    public static final int DEFAULT_SIZE_PER_PAGE = 20;
    public static final int MAX_SIZE_PER_PAGE = 100;

    private PageUtils() {
    }

    public static int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return 1;
        }
        return page;
    }

    public static int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE_PER_PAGE;
        }
        if (size <= 0) {
            return MAX_SIZE_PER_PAGE;
        }
        return size;
    }

    /** 供 SQL 用的偏移量（厂商用 PageHelper，等价于 {@code (page-1)*size}）。 */
    public static int offset(int page, int size) {
        return (page - 1) * size;
    }
}
