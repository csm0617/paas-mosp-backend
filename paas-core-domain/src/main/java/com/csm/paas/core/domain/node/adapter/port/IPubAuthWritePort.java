package com.csm.paas.core.domain.node.adapter.port;

/**
 * `g_service_auth` 的**写**入口（厂商 `AuthService.authing:210-230` 那段 upsert）。
 *
 * <p>为什么另起端口：厂商用 `serviceAuthMapper.selectByPrimaryKey` + `updateByPrimaryKeySelective`
 * / `insertSelective`，而本仓既有的 `IServiceAuthRepository`（software 域）**只有读与"改过期"**
 * ——本次任务白名单不放行 `domain/software/**` 与既有 `dao/IServiceAuthDao.java`，
 * 故新增本端口，由 infrastructure 的 gateway 用 `DataSource` 直写。
 *
 * <p>厂商写库的列（`dao/ServiceAuthDynamicSqlSupport.java:24-31`）：
 * `auth`、`auth_type`、`auth_time`、`auth_code`（更新）+ `service_id`、`created_at`（插入）。
 * `updated_at` 由 DDL 的 `ON UPDATE CURRENT_TIMESTAMP` 维护（vendor `gaop-9.sql:777`），厂商不显式写。
 */
public interface IPubAuthWritePort {

    /**
     * 厂商 `AuthService.authing` 的单条 upsert：**存在就 update、否则 insert**
     * （判断用 `selectByPrimaryKey(serviceId).isPresent()`——厂商那段 `serviceAuth1 != null`
     * 是多余判断，MyBatis-dynamic-sql 的 `selectByPrimaryKey` 从不返回 null）。
     *
     * @param authType 授权方式（厂商 `AuthType.onceAuth.type()` = 1）
     * @param auth     授权状态（厂商 `AuthServiceType.TOBEAUTHORIZED` = 3）
     * @param authTime 授权到期时间（厂商取 JWT 的 `expiresAt.getTime()`）
     * @param authCode 授权码（厂商取 JWT 的 `gaop` claim）
     * @return **影响行数是否为 1**（厂商 `if (updateResult == 1)` / `if (insertResult == 1)`）
     */
    boolean upsertServiceAuth(String serviceId, Integer auth, Integer authType, Long authTime, String authCode);
}
