package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.EncryFormDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.dynamicloading.service.IDynamicLoadingCommandService;
import com.csm.paas.core.trigger.crypto.AesPayloadDecryptor;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 厂商 `DynamicLoadingController`（`/v1/load`）——本次只实现 `POST /update`。
 *
 * <p>未实现：`POST /upload`（上传授权 jar，要动态类加载）、`GET /list`（授权 jar 列表）。
 *
 * <p><b>分层说明</b>：厂商把 AES 解密 + Gson 解析 + 写库全放在
 * `DynamicLoadingService.updateDefaultAuth:64-97`；这里把前两步（需要 Jackson）留在 trigger，
 * 只把写库交给领域服务，对外可观测行为一致。
 */
@RestController
@RequestMapping("/v1/load")
@RequiredArgsConstructor
public class DynamicLoadingController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IDynamicLoadingCommandService dynamicLoadingCommandService;

    /**
     * 厂商 `POST /v1/load/update`（`DynamicLoadingController.java:31-37`）：
     * body `EncryForm{publicKey}` ⇒ 更新默认授权 jar 的密钥，返回布尔。
     *
     * <p>照抄点：控制器先 `replaceAll("\\s*|\t|\r|\n","")`，服务里再 `replaceAll(" +","")`；
     * AES 解密失败 ⇒ 400 `auth.parse.update.auth.public.key.error`；解密串还要再剥掉
     * `\r`/`\n` 与 `-----BEGIN/END (PUBLIC|PRIVATE) KEY-----` 包裹。
     * **JSON 解析失败厂商不兜**（Gson 抛未捕获异常 ⇒ 500），我们同样不兜（Jackson 的 IOException 直接冒到 500）。
     */
    @PostMapping("/update")
    public ResponseDTO<Boolean> updateDefaultAuth(@RequestBody EncryFormDTO form) throws IOException {
        // 厂商控制器 :34
        String publicKey = form.getPublicKey().replaceAll("\\s*|\t|\r|\n", "");
        // 厂商服务 :65 的第二次清洗
        publicKey = publicKey.replaceAll(" +", "");

        String decrypt;
        try {
            decrypt = AesPayloadDecryptor.decrypt(publicKey, AesPayloadDecryptor.ID_KEY);
        } catch (Exception e) {
            throw new WrongArgumentException("auth.parse.update.auth.public.key.error");
        }
        // 厂商 :73-74：剥掉转义换行与 PEM 包裹
        decrypt = decrypt.replace("\\r", "").replace("\\n", "")
                .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "");

        JsonNode loadInfo = OBJECT_MAPPER.readTree(decrypt);
        String privateKey = loadInfo.path("privateKey").asText(null);
        String key = loadInfo.path("publicKey").asText(null);
        return ResponseDTO.ok(dynamicLoadingCommandService.updateDefaultAuth(key, privateKey));
    }
}
