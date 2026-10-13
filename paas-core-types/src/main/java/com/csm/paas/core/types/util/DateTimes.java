package com.csm.paas.core.types.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * 日期与时区的换算——**这里藏着一条实测出来的兼容性坑**。
 *
 * <p>背景：`g_user.birth` 是 MySQL 的 **`date` 列**（只有年月日），而厂商的 Java 模型是 `java.util.Date`。
 * 厂商把 DATE 读成"**JVM 默认时区的当天零点**"的瞬时值，再用 Jackson 以 UTC 输出：
 * 在 `TZ=Asia/Shanghai` 下，`2000-01-02` → `2000-01-01T16:00:00.000Z`（实测）。
 *
 * <p>我们用的是 Connector/J **8.3**，厂商用的是 **5.1.48**，两者把 DATE 读成 `Timestamp` 的时区语义不同：
 * 同样是 `serverTimezone=UTC`，8.x 给的是 UTC 零点、5.1 给的是 JVM 本地零点 ⇒ **JSON 里差 8 小时**（实测对拉发现）。
 *
 * <p>因此这里**不再依赖驱动的隐式语义**：领域层用 `LocalDate` 表示"日期"，
 * 进出 JSON 时按 {@link ZoneId#systemDefault()} 显式换算，与厂商在同一个 TZ 下的表现一致。
 * 部署时容器 TZ 应与厂商一致（厂商 pod 设了 `TZ=Asia/Shanghai`）。
 */
public final class DateTimes {

    private DateTimes() {
    }

    /** 日期 → "该日期在 JVM 默认时区的零点"这一瞬时（厂商的观测语义）。 */
    public static Date toDate(LocalDate date) {
        return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** 瞬时 → "该瞬时在 JVM 默认时区下的日期"。 */
    public static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
