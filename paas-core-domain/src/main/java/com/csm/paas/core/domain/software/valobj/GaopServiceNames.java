package com.csm.paas.core.domain.software.valobj;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 厂商 `constant/GaopName.java` 的等价物：框架/基础服务的名单与显示名。
 * <p>这些是**厂商业务数据**，照抄，不要顺手整理。
 * <p><b>{@link #BASE} 在当前厂商实现里是死数据</b>：`GET /v1/service/gaop` 在"没有
 * `system/appType` 标签"时，`type==1` 与 `type==2` 都走 {@link #PASS_FRAME}
 * （厂商两行是复制粘贴的同一句，见 `docs/findings/53` §5）⇒ 查"基础服务"实际返回框架服务。照抄该行为。
 */
public final class GaopServiceNames {

    private GaopServiceNames() {
    }

    public static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("gaop-be", "框架后端"), Map.entry("gaop-web", "框架前端"),
            Map.entry("gaop-mysql", "框架数据库"), Map.entry("gateway", "网关"),
            Map.entry("gaop-redis", "框架redis"), Map.entry("gaop-auth", "框架授权"),
            Map.entry("gaop-startup", "框架分批启动控制"), Map.entry("gaop-gateway-auth", "网关"),
            Map.entry("gaop-login", "统一登录"), Map.entry("gaop-fdfs", "文件存储"),
            Map.entry("gaop-image-uploader", "镜像服务"), Map.entry("zk", "zookeepper"),
            Map.entry("gaop-radondb-mysql-leader", "业务数据库"), Map.entry("mysql", "业务数据库"),
            Map.entry("redis", "业务redis"), Map.entry("kafka", "kafka"),
            Map.entry("gis-server", "地图服务"), Map.entry("gis-jb", "军标服务"));

    public static final Set<String> PASS_FRAME = Set.of(
            "gaop-be", "gaop-web", "gaop-redis", "gateway", "gaop-mysql",
            "gaop-auth", "gaop-startup", "gaop-gateway-auth", "gaop-login");

    public static final Set<String> BASE = Set.of(
            "gaop-fdfs", "gaop-image-uploader", "zk", "gaop-radondb-mysql-leader",
            "redis", "kafka", "mysql");

    public static final String APP_TYPE_LABEL = "system/appType";
    public static final String APP_TYPE_GAOP_CORE = "gaop-core";
    public static final String APP_TYPE_GAOP_BASE = "gaop-base";
    public static final String APP_TYPE_BUSINESS = "business";

    public static boolean serviceInPassFrame(String name) {
        return name != null && PASS_FRAME.contains(name);
    }

    public static boolean serviceInBaseService(String name) {
        return name != null && BASE.contains(name);
    }

    /** 厂商是 `gaopName.get(name)`（取不到为 null）；我们回退原名，避免下发 null——待对拉确认。 */
    public static String fixedName(String name) {
        return DISPLAY_NAMES.getOrDefault(name, name);
    }

    public static List<String> allNames() {
        return java.util.stream.Stream.concat(PASS_FRAME.stream(), BASE.stream()).distinct().sorted().toList();
    }
}
