package com.csm.paas.core.domain.licence.adapter.port;

/**
 * 解出"框架公私钥"载荷里的公钥（厂商 `LicenceService.updateFrameworkRsaKey:1078-1107` 里那段
 * AES 解密 + 反转义 + 去 PEM 头 + Gson 解析）。
 *
 * <p>放端口后面：domain 不碰 AES/JSON。
 * <p>解不出来 ⇒ 抛 `WrongArgumentException("licence.parse.authorization.info.failed")`。
 */
public interface ILicencePayloadPort {

    /** 入参是前端传来的 `encryptInfo`（Base64 密文，可能带空格）。 */
    String decryptPublicKey(String encryptInfo);
}
