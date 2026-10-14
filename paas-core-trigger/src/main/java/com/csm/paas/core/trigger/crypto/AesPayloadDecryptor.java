package com.csm.paas.core.trigger.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 厂商 `com.glriverside.gaop.be.config.AesConfig` 的 AES 解密部分
 * （`AesConfig.java:38-45`；加密方法本次不用，故不搬）。
 *
 * <p>照抄点：
 * <ul>
 *   <li>算法 `AES/ECB/PKCS5Padding`，密钥是**明文字符串直接 getBytes()**（16 字节 ⇒ AES-128），
 *       硬编码 `2aea937082964c17`（厂商 `AesConfig.ID_KEY`）；</li>
 *   <li>入参是 Base64；厂商 `base64Decode` 对空串返回 **null**，紧接着 `decode.length` 会 NPE
 *       ——我们照抄这个 NPE（由调用方 catch 成 400）；</li>
 *   <li>厂商把解码后的字节长度 `System.out.println` 出来，我们也照抄这行输出（怪点，不修）。</li>
 * </ul>
 */
public final class AesPayloadDecryptor {

    private static final String KEY_ALGORITHM = "AES";
    private static final String ALGORITHM_STR = "AES/ECB/PKCS5Padding";
    private static final String CHAR_SET = "UTF-8";

    /** 厂商 `AesConfig.ID_KEY`：硬编码密钥。 */
    public static final String ID_KEY = "2aea937082964c17";

    private AesPayloadDecryptor() {
    }

    /**
     * 厂商 `AesConfig.aesDecrypt(encryptContent, password)`（`AesConfig.java:38-45`）。
     */
    public static String decrypt(String encryptContent, String password) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM_STR);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(password.getBytes(), KEY_ALGORITHM));
        byte[] decode = (encryptContent == null || encryptContent.isEmpty())
                ? null
                : Base64.getDecoder().decode(encryptContent);
        // 照抄厂商的调试输出（空串时这里 NPE，与厂商一致）
        System.out.println("解码字节数组长度：" + decode.length);
        byte[] result = cipher.doFinal(decode);
        return new String(result, CHAR_SET);
    }
}
