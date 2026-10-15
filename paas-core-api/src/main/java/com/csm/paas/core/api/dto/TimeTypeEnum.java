package com.csm.paas.core.api.dto;

import java.util.Date;

/**
 * 厂商 `constant/TimeTypeEnum`：`/v1/pub/nodeMonitor` 与 `/v1/pub/serviceMonitor` 的
 * `timeType` 查询参数（`queryTimeType=true` 时必填）。
 *
 * <p>逐字照抄厂商的枚举常量名与时间窗（`TimeTypeEnum:8-54`）：
 * 1/6/24 小时、7 天、30 天；`getStartTime` 用毫秒常量直接减，`getEndTime` 就是"现在"。
 * <p>常量名必须与厂商一致，否则前端传的 `timeType=ONE_HOURS` 无法绑定。
 */
public enum TimeTypeEnum {

    /** 最近 1 小时（厂商 `ONE_HOURS(1, "最近1小时")`）。 */
    ONE_HOURS(1, "最近1小时"),

    /** 最近 6 小时。 */
    SIX_HOURS(6, "最近6小时"),

    /** 最近 24 小时。 */
    TWENTY_FOUR_HOURS(24, "最近24小时"),

    /** 最近 7 天。 */
    SEVEN_DAY(7, "最近7天"),

    /** 最近 30 天。 */
    THIRTY_DAY(30, "最近30天");

    private final int value;
    public final String desc;

    TimeTypeEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /** 厂商 `getStartTime:18-37`：五个 `case` 各自返回 `now - 固定毫秒`。 */
    public Date getStartTime(Long now) {
        switch (this) {
            case ONE_HOURS:
                return new Date(now - 3600000L);
            case SIX_HOURS:
                return new Date(now - 21600000L);
            case TWENTY_FOUR_HOURS:
                return new Date(now - 86400000L);
            case SEVEN_DAY:
                return new Date(now - 604800000L);
            case THIRTY_DAY:
                return new Date(now - 2592000000L);
            default:
                return new Date(now);
        }
    }

    /** 厂商 `getEndTime:39-41`：恒为"现在"。 */
    public Date getEndTime(Long now) {
        return new Date(now);
    }

    public int getValue() {
        return this.value;
    }

    public String getDesc() {
        return this.desc;
    }
}
