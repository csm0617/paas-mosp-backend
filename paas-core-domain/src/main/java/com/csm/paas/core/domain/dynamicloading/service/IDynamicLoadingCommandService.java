package com.csm.paas.core.domain.dynamicloading.service;

/**
 * 动态加载授权 jar 的写操作（厂商 `DynamicLoadingService`，`/v1/load`）。
 */
public interface IDynamicLoadingCommandService {

    /**
     * 更新默认授权 jar 的公私钥（厂商 `DynamicLoadingService.updateDefaultAuth:64-97`）。
     *
     * <p>厂商流程：控制器先 `replaceAll("\\s*|\t|\r|\n","")`，服务里再 `replaceAll(" +","")`，
     * 然后 **AES/ECB/PKCS5Padding（密钥 `2aea937082964c17`）解密 Base64** ⇒ 得到一段 JSON
     * `{privateKey, publicKey}`（`DynamicLoadInfo`）⇒ 取 `g_auth` 里 `jar_name='default_auth'`
     * 的最新一条，命中则写 `used=1` + 两个密钥 + `update_at=now` 并返回 `true`，否则返回 `false`。
     *
     * <p><b>分层调整</b>：AES 与 JSON 解析（Jackson）放在 trigger（`DynamicLoadingController` +
     * `AesPayloadDecryptor`），域服务只收已解出的两个密钥串——与厂商的"一个方法全做完"等价。
     *
     * @param publicKey  解密后 JSON 里的 `publicKey`（厂商变量名 `key`）
     * @param privateKey 解密后 JSON 里的 `privateKey`
     */
    boolean updateDefaultAuth(String publicKey, String privateKey);
}
