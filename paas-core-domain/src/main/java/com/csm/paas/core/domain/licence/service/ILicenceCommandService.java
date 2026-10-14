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

    /**
     * 更新框架公私钥（厂商 `POST /v1/licence/updateFramework` → `LicenceService.updateFrameworkRsaKey:1078-1107`，
     * 规格见 `docs/findings/151`）。
     *
     * <p>`encryptInfo` 为 null ⇒ 400 `licence.new.public.key.empty`；随后交给载荷端口解密
     * （失败 ⇒ 400 `licence.parse.authorization.info.failed`）；再更新 `g_activate` 里
     * `name='gaop'` 那行的 `public_key`/`update_at`，**返回受影响行数 > 0**。
     */
    boolean updateFrameworkRsaKey(String encryptInfo);
}
