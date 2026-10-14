package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.licence.adapter.port.ILicencePayloadPort;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 厂商 `AesConfig`（`AES/ECB/PKCS5Padding` + **写死的密钥** `2aea937082964c17`）与
 * `updateFrameworkRsaKey` 里的清洗步骤，逐行照抄：
 * <ol>
 *   <li>去掉密文里的**所有空格**；</li>
 *   <li>AES/ECB/PKCS5 解密（Base64 密文，UTF-8 明文）；</li>
 *   <li>`StringEscapeUtils.unescapeJava`；</li>
 *   <li>去掉字面量 `\r`/`\n` 与四个 PEM 头尾标记（**在整段 JSON 文本上做**，不是只对字段）；</li>
 *   <li>Gson（我们等价用 Jackson）解析出 `publicKey` 字段。</li>
 * </ol>
 * 任何一步失败 ⇒ 400 `licence.parse.authorization.info.failed`。
 */
@Slf4j
@Component
public class LicencePayloadPort implements ILicencePayloadPort {

    /** 厂商 `AesConfig.ID_KEY`（写死的 AES 密钥，照抄）。 */
    private static final String ID_KEY = "2aea937082964c17";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String decryptPublicKey(String encryptInfo) {
        try {
            String normalized = encryptInfo.replaceAll(" +", "");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(ID_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
            String decrypted = new String(cipher.doFinal(Base64.getDecoder().decode(normalized)),
                    StandardCharsets.UTF_8);
            String json = StringEscapeUtils.unescapeJava(decrypted)
                    .replace("\\r", "").replace("\\n", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "");
            JsonNode node = MAPPER.readTree(json);
            return node.path("publicKey").asText(null);
        } catch (Exception e) {
            log.error("aes解密失败，异常为:{}", e.toString());
            throw new WrongArgumentException("licence.parse.authorization.info.failed");
        }
    }
}
