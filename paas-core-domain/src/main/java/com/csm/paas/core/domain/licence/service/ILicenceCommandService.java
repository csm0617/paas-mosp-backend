package com.csm.paas.core.domain.licence.service;

/** 授权/许可写操作（厂商 `LicenceController` 的 `POST /updateFramework`、`GET /loginOutAllUser` 等）。 */
public interface ILicenceCommandService {

    /**
     * 清理所有在线用户（厂商 `GET /v1/licence/loginOutAllUser` →
     * `LicenceService.loginOutAllUser:1146-1149` = `redisService.deleteAllRedisKey()`）。
     *
     * <p>厂商只删 `gateway-token:*` 与 `login-list:*` 两类键（**不是 FLUSHALL**），恒返回 true。
     */
    boolean loginOutAllUsers();
}
